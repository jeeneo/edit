// jpeg_transform.h - co-created with deepseek-v4-flash
#ifndef JPEG_TRANSFORM_H
#define JPEG_TRANSFORM_H

#include <jni.h>

extern "C"
JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_domain_ops_lossless_JPEGLosslessTransform_nativeCropLossless(
        JNIEnv *env, jobject thiz,
        jbyteArray inputBytes, jint x, jint y, jint w, jint h,
        jobject templateObj);

extern "C"
JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_domain_ops_lossless_JPEGLosslessTransform_nativeRotate90Lossless(
        JNIEnv *env, jobject thiz,
        jbyteArray inputBytes, jint quarterTurns, jboolean trim,
        jobject templateObj);

extern "C"
JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_domain_ops_lossless_JPEGLosslessTransform_nativeGetMcuSize(
        JNIEnv *env, jobject thiz,
        jbyteArray inputBytes,
        jobject templateObj);

#endif // JPEG_TRANSFORM_H
