//
// Copyright (c) 2026 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

use std::{mem, sync::Arc};

use jni::{
    objects::{JClass, JObject, JObjectArray, JValue},
    sys::{jboolean, jbyteArray, jint, jlong, jobject},
    JNIEnv,
};
use zenoh::{
    config::WhatAmI,
    handlers::Callback,
    session::{
        Link, LinkEvent, LinkEventsListener, Session, Transport, TransportEvent,
        TransportEventsListener,
    },
    Wait,
};

use crate::{
    errors::ZResult,
    throw_exception,
    utils::{get_callback_global_ref, get_java_vm, load_on_close},
    zerror,
};

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

fn whatami_to_int(whatami: WhatAmI) -> jint {
    match whatami {
        WhatAmI::Router => 1,
        WhatAmI::Peer => 2,
        WhatAmI::Client => 4,
    }
}

fn decode_whatami(value: jint) -> ZResult<WhatAmI> {
    match value {
        1 => Ok(WhatAmI::Router),
        2 => Ok(WhatAmI::Peer),
        4 => Ok(WhatAmI::Client),
        v => Err(zerror!("Unknown WhatAmI value: {}", v)),
    }
}

fn reliability_to_int(reliability: zenoh::qos::Reliability) -> jint {
    match reliability {
        zenoh::qos::Reliability::BestEffort => 0,
        zenoh::qos::Reliability::Reliable => 1,
    }
}

unsafe fn decode_byte_array_raw(env: &mut JNIEnv, array: jbyteArray) -> ZResult<Vec<u8>> {
    use jni::objects::JByteArray;
    let jarray = JByteArray::from_raw(array);
    let len = env.get_array_length(&jarray).map_err(|e| zerror!(e))? as usize;
    let mut buf = vec![0i8; len];
    env.get_byte_array_region(&jarray, 0, &mut buf)
        .map_err(|e| zerror!(e))?;
    Ok(std::mem::transmute::<Vec<i8>, Vec<u8>>(buf))
}

/// Decode nullable jbyteArray + transport fields into an optional Transport filter.
unsafe fn decode_transport_filter(
    env: &mut JNIEnv,
    transport_zid: jbyteArray,
    transport_whatami: jint,
    transport_is_qos: jboolean,
    transport_is_multicast: jboolean,
) -> ZResult<Option<Transport>> {
    if transport_zid.is_null() {
        return Ok(None);
    }
    let zid_bytes = decode_byte_array_raw(env, transport_zid)?;
    let zid = zenoh::session::ZenohId::try_from(zid_bytes.as_slice())
        .map_err(|err| zerror!("Failed to decode ZenohId: {}", err))?;
    let whatami = decode_whatami(transport_whatami)?;
    let transport = Transport::new_from_fields(
        zid,
        whatami,
        transport_is_qos != 0,
        transport_is_multicast != 0,
    );
    Ok(Some(transport))
}

fn int_obj<'a>(env: &mut JNIEnv<'a>, val: jint) -> ZResult<JObject<'a>> {
    env.call_static_method(
        "java/lang/Integer",
        "valueOf",
        "(I)Ljava/lang/Integer;",
        &[JValue::from(val)],
    )
    .map_err(|e| zerror!(e))?
    .l()
    .map_err(|e| zerror!(e))
}

fn bool_obj<'a>(env: &mut JNIEnv<'a>, val: bool) -> ZResult<JObject<'a>> {
    env.call_static_method(
        "java/lang/Boolean",
        "valueOf",
        "(Z)Ljava/lang/Boolean;",
        &[JValue::from(val as jboolean)],
    )
    .map_err(|e| zerror!(e))?
    .l()
    .map_err(|e| zerror!(e))
}

/// Encode a Transport as an Object[4] array: [zidBytes, Integer(whatami), Boolean(isQos), Boolean(isMulticast)]
fn transport_to_java_array<'a>(
    env: &mut JNIEnv<'a>,
    transport: &Transport,
) -> ZResult<JObjectArray<'a>> {
    let zid_bytes = env
        .byte_array_from_slice(&transport.zid().to_le_bytes())
        .map_err(|e| zerror!(e))?;
    let whatami_obj = int_obj(env, whatami_to_int(transport.whatami()))?;
    let is_qos_obj = bool_obj(env, transport.is_qos())?;
    let is_multicast_obj = bool_obj(env, transport.is_multicast())?;

    let arr = env
        .new_object_array(4, "java/lang/Object", JObject::null())
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 0, zid_bytes)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 1, whatami_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 2, is_qos_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 3, is_multicast_obj)
        .map_err(|e| zerror!(e))?;

    Ok(arr)
}

/// Encode a Link as an Object[12] array matching Kotlin's expected layout:
/// [0]=zidBytes, [1]=src, [2]=dst, [3]=group?, [4]=mtu, [5]=isStreamed,
/// [6]=interfaces[], [7]=authId?, [8]=unused, [9]=priorityMin, [10]=priorityMax, [11]=reliability
fn link_to_java_array<'a>(env: &mut JNIEnv<'a>, link: &Link) -> ZResult<JObjectArray<'a>> {
    // Pre-compute all objects before creating/filling the array to avoid borrow conflicts
    let zid_bytes = env
        .byte_array_from_slice(&link.zid().to_le_bytes())
        .map_err(|e| zerror!(e))?;
    let src = env
        .new_string(link.src().to_string())
        .map_err(|e| zerror!(e))?;
    let dst = env
        .new_string(link.dst().to_string())
        .map_err(|e| zerror!(e))?;
    let group: Option<jni::objects::JString> = if let Some(g) = link.group() {
        Some(env.new_string(format!("{}", g)).map_err(|e| zerror!(e))?)
    } else {
        None
    };
    let mtu_obj = int_obj(env, link.mtu() as jint)?;
    let is_streamed_obj = bool_obj(env, link.is_streamed())?;

    let ifaces = link.interfaces();
    let ifaces_arr = env
        .new_object_array(ifaces.len() as jint, "java/lang/String", JObject::null())
        .map_err(|e| zerror!(e))?;
    for (i, iface) in ifaces.iter().enumerate() {
        let s = env.new_string(iface).map_err(|e| zerror!(e))?;
        env.set_object_array_element(&ifaces_arr, i as jint, s)
            .map_err(|e| zerror!(e))?;
    }

    let auth: Option<jni::objects::JString> = if let Some(a) = link.auth_identifier() {
        Some(env.new_string(a).map_err(|e| zerror!(e))?)
    } else {
        None
    };
    let dummy = int_obj(env, 0)?;
    let pmin = link.priorities().map(|(min, _)| min as jint).unwrap_or(-1);
    let pmin_obj = int_obj(env, pmin)?;
    let pmax = link.priorities().map(|(_, max)| max as jint).unwrap_or(-1);
    let pmax_obj = int_obj(env, pmax)?;
    let rel = link.reliability().map(reliability_to_int).unwrap_or(-1);
    let rel_obj = int_obj(env, rel)?;

    let arr = env
        .new_object_array(12, "java/lang/Object", JObject::null())
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 0, zid_bytes)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 1, src)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 2, dst)
        .map_err(|e| zerror!(e))?;
    if let Some(g) = group {
        env.set_object_array_element(&arr, 3, g)
            .map_err(|e| zerror!(e))?;
    }
    env.set_object_array_element(&arr, 4, mtu_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 5, is_streamed_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 6, ifaces_arr)
        .map_err(|e| zerror!(e))?;
    if let Some(a) = auth {
        env.set_object_array_element(&arr, 7, a)
            .map_err(|e| zerror!(e))?;
    }
    env.set_object_array_element(&arr, 8, dummy)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 9, pmin_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 10, pmax_obj)
        .map_err(|e| zerror!(e))?;
    env.set_object_array_element(&arr, 11, rel_obj)
        .map_err(|e| zerror!(e))?;

    Ok(arr)
}

// ──────────────────────────────────────────────────────────────────────────────
// getTransports
// ──────────────────────────────────────────────────────────────────────────────

/// Returns a Java ArrayList of Transport snapshots. Each element is an Object[4] array.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_getTransportsViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
) -> jobject {
    let session = Arc::from_raw(session_ptr);
    let result = || -> ZResult<jobject> {
        let transports = session.info().transports().wait();
        let list = env
            .new_object("java/util/ArrayList", "()V", &[])
            .map_err(|e| zerror!(e))?;
        use jni::objects::JList;
        let jlist = JList::from_env(&mut env, &list).map_err(|e| zerror!(e))?;
        for t in transports {
            let arr = transport_to_java_array(&mut env, &t)?;
            jlist.add(&mut env, &arr).map_err(|e| zerror!(e))?;
        }
        Ok(list.as_raw())
    }();
    mem::forget(session);
    result.unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::default().as_raw()
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// getLinks
// ──────────────────────────────────────────────────────────────────────────────

/// Returns a Java ArrayList of Link snapshots, optionally filtered by transport.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_getLinksViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    transport_zid: jbyteArray,
    transport_whatami: jint,
    transport_is_qos: jboolean,
    transport_is_multicast: jboolean,
) -> jobject {
    let session = Arc::from_raw(session_ptr);
    let result = || -> ZResult<jobject> {
        let transport_filter = decode_transport_filter(
            &mut env,
            transport_zid,
            transport_whatami,
            transport_is_qos,
            transport_is_multicast,
        )?;

        let links = {
            let info = session.info();
            let builder = info.links();
            match transport_filter {
                Some(t) => builder.transport(t).wait(),
                None => builder.wait(),
            }
        };

        let list = env
            .new_object("java/util/ArrayList", "()V", &[])
            .map_err(|e| zerror!(e))?;
        use jni::objects::JList;
        let jlist = JList::from_env(&mut env, &list).map_err(|e| zerror!(e))?;
        for link in links {
            let arr = link_to_java_array(&mut env, &link)?;
            jlist.add(&mut env, &arr).map_err(|e| zerror!(e))?;
        }
        Ok(list.as_raw())
    }();
    mem::forget(session);
    result.unwrap_or_else(|err| {
        throw_exception!(env, err);
        JObject::default().as_raw()
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// Build transport events callback closure
// ──────────────────────────────────────────────────────────────────────────────

unsafe fn make_transport_events_callback(
    env: &mut JNIEnv,
    callback: JObject,
    on_close: JObject,
) -> ZResult<Callback<TransportEvent>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let on_close = load_on_close(&java_vm, on_close_global_ref);

    Ok(Callback::from(move |event: TransportEvent| {
        on_close.noop();
        let _ = || -> ZResult<()> {
            let mut env = java_vm
                .attach_current_thread_as_daemon()
                .map_err(|err| zerror!("Unable to attach thread for transport events: {}", err))?;

            let kind = event.kind() as jint;
            let zid_bytes = env
                .byte_array_from_slice(&event.transport().zid().to_le_bytes())
                .map_err(|e| zerror!(e))?;
            let whatami = whatami_to_int(event.transport().whatami());
            let is_qos = event.transport().is_qos() as jboolean;
            let is_multicast = event.transport().is_multicast() as jboolean;

            env.call_method(
                &callback_global_ref,
                "run",
                "(I[BIZZ)V",
                &[
                    JValue::from(kind),
                    JValue::from(&zid_bytes),
                    JValue::from(whatami),
                    JValue::from(is_qos),
                    JValue::from(is_multicast),
                ],
            )
            .map_err(|e| zerror!(e))?;
            Ok(())
        }()
        .map_err(|err| tracing::error!("On transport events callback error: {err}"));
    }))
}

// ──────────────────────────────────────────────────────────────────────────────
// Build link events callback closure
// ──────────────────────────────────────────────────────────────────────────────

unsafe fn make_link_events_callback(
    env: &mut JNIEnv,
    callback: JObject,
    on_close: JObject,
) -> ZResult<Callback<LinkEvent>> {
    let java_vm = Arc::new(get_java_vm(env)?);
    let callback_global_ref = get_callback_global_ref(env, callback)?;
    let on_close_global_ref = get_callback_global_ref(env, on_close)?;
    let on_close = load_on_close(&java_vm, on_close_global_ref);

    Ok(Callback::from(move |event: LinkEvent| {
        on_close.noop();
        let _ = || -> ZResult<()> {
            let mut env = java_vm.attach_current_thread_as_daemon()
                .map_err(|err| zerror!("Unable to attach thread for link events: {}", err))?;

            let kind = event.kind() as jint;
            let zid_bytes = env.byte_array_from_slice(&event.link().zid().to_le_bytes())
                .map_err(|e| zerror!(e))?;
            let src = env.new_string(event.link().src().to_string()).map_err(|e| zerror!(e))?;
            let dst = env.new_string(event.link().dst().to_string()).map_err(|e| zerror!(e))?;

            let group: JObject = match event.link().group() {
                Some(g) => env.new_string(format!("{}", g)).map_err(|e| zerror!(e))?.into(),
                None => JObject::null(),
            };

            let mtu = event.link().mtu() as jint;
            let is_streamed = event.link().is_streamed() as jboolean;

            let ifaces = event.link().interfaces();
            let ifaces_arr = env.new_object_array(ifaces.len() as jint, "java/lang/String", JObject::null())
                .map_err(|e| zerror!(e))?;
            for (i, iface) in ifaces.iter().enumerate() {
                let s = env.new_string(iface).map_err(|e| zerror!(e))?;
                env.set_object_array_element(&ifaces_arr, i as jint, s).map_err(|e| zerror!(e))?;
            }

            let auth: JObject = match event.link().auth_identifier() {
                Some(a) => env.new_string(a).map_err(|e| zerror!(e))?.into(),
                None => JObject::null(),
            };

            let pmin = event.link().priorities().map(|(min, _)| min as jint).unwrap_or(-1);
            let pmax = event.link().priorities().map(|(_, max)| max as jint).unwrap_or(-1);
            let rel = event.link().reliability().map(reliability_to_int).unwrap_or(-1);

            env.call_method(
                &callback_global_ref,
                "run",
                "(I[BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ[Ljava/lang/String;Ljava/lang/String;III)V",
                &[
                    JValue::from(kind),
                    JValue::from(&zid_bytes),
                    JValue::from(&src),
                    JValue::from(&dst),
                    JValue::from(&group),
                    JValue::from(mtu),
                    JValue::from(is_streamed),
                    JValue::from(&ifaces_arr),
                    JValue::from(&auth),
                    JValue::from(pmin),
                    JValue::from(pmax),
                    JValue::from(rel),
                ],
            ).map_err(|e| zerror!(e))?;
            Ok(())
        }()
        .map_err(|err| tracing::error!("On link events callback error: {err}"));
    }))
}

// ──────────────────────────────────────────────────────────────────────────────
// Transport events listeners
// ──────────────────────────────────────────────────────────────────────────────

/// Declare a transport events listener via JNI. Returns a raw pointer to the listener (Arc-wrapped).
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareTransportEventsListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    history: jboolean,
) -> jlong {
    let session = Arc::from_raw(session_ptr);
    let result = || -> ZResult<jlong> {
        let cb = make_transport_events_callback(&mut env, callback, on_close)?;
        let listener = session
            .info()
            .transport_events_listener()
            .history(history != 0)
            .with(cb)
            .wait()
            .map_err(|err| zerror!("Unable to declare transport events listener: {}", err))?;
        Ok(Arc::into_raw(Arc::new(listener)) as jlong)
    }();
    mem::forget(session);
    result.unwrap_or_else(|err| {
        throw_exception!(env, err);
        0
    })
}

/// Declare a background transport events listener via JNI.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareBackgroundTransportEventsListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    history: jboolean,
) {
    let session = Arc::from_raw(session_ptr);
    let result = || -> ZResult<()> {
        let cb = make_transport_events_callback(&mut env, callback, on_close)?;
        session
            .info()
            .transport_events_listener()
            .history(history != 0)
            .with(cb)
            .background()
            .wait()
            .map_err(|err| {
                zerror!(
                    "Unable to declare background transport events listener: {}",
                    err
                )
            })
    }();
    mem::forget(session);
    result.unwrap_or_else(|err| throw_exception!(env, err));
}

/// Frees the TransportEventsListener.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNITransportEventsListener_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const TransportEventsListener<()>,
) {
    Arc::from_raw(ptr);
}

// ──────────────────────────────────────────────────────────────────────────────
// Link events listeners
// ──────────────────────────────────────────────────────────────────────────────

/// Declare a link events listener via JNI. Returns a raw pointer to the listener (Arc-wrapped).
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareLinkEventsListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    history: jboolean,
    transport_zid: jbyteArray,
    transport_whatami: jint,
    transport_is_qos: jboolean,
    transport_is_multicast: jboolean,
) -> jlong {
    let session = Arc::from_raw(session_ptr);
    let result = || -> ZResult<jlong> {
        let transport_filter = decode_transport_filter(
            &mut env,
            transport_zid,
            transport_whatami,
            transport_is_qos,
            transport_is_multicast,
        )?;
        let cb = make_link_events_callback(&mut env, callback, on_close)?;

        let info = session.info();
        let mut builder = info.link_events_listener().history(history != 0);
        if let Some(t) = transport_filter {
            builder = builder.transport(t);
        }
        let listener = builder
            .with(cb)
            .wait()
            .map_err(|err| zerror!("Unable to declare link events listener: {}", err))?;
        Ok(Arc::into_raw(Arc::new(listener)) as jlong)
    }();
    mem::forget(session);
    result.unwrap_or_else(|err| {
        throw_exception!(env, err);
        0
    })
}

/// Declare a background link events listener via JNI.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNISession_declareBackgroundLinkEventsListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    session_ptr: *const Session,
    callback: JObject,
    on_close: JObject,
    history: jboolean,
    transport_zid: jbyteArray,
    transport_whatami: jint,
    transport_is_qos: jboolean,
    transport_is_multicast: jboolean,
) {
    let session = Arc::from_raw(session_ptr);
    let result =
        || -> ZResult<()> {
            let transport_filter = decode_transport_filter(
                &mut env,
                transport_zid,
                transport_whatami,
                transport_is_qos,
                transport_is_multicast,
            )?;
            let cb = make_link_events_callback(&mut env, callback, on_close)?;

            let info = session.info();
            let mut builder = info.link_events_listener().history(history != 0);
            if let Some(t) = transport_filter {
                builder = builder.transport(t);
            }
            builder.with(cb).background().wait().map_err(|err| {
                zerror!("Unable to declare background link events listener: {}", err)
            })
        }();
    mem::forget(session);
    result.unwrap_or_else(|err| throw_exception!(env, err));
}

/// Frees the LinkEventsListener.
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNILinkEventsListener_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const LinkEventsListener<()>,
) {
    Arc::from_raw(ptr);
}
