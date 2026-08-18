// jpeg_transform.cpp - co-created with deepseek-v4-flash

#include "jpeg_transform.h"
#include <turbojpeg.h>
#include <android/log.h>
#include <vector>
#include <cstring>

namespace {
    jobject makeResult(JNIEnv *env, jobject templateObj,
                       const unsigned char *data, unsigned long size,
                       int x, int y, int w, int h) {

        jclass cls = env->GetObjectClass(templateObj);
        jmethodID ctor = env->GetMethodID(cls, "<init>", "([BIIII)V");
        if (ctor == nullptr) {
            env->DeleteLocalRef(cls);
            return nullptr;
        }

        jbyteArray out = env->NewByteArray(static_cast<jsize>(size));
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(size),
                                reinterpret_cast<const jbyte *>(data));

        jobject result = env->NewObject(cls, ctor, out, x, y, w, h);
        env->DeleteLocalRef(cls);
        env->DeleteLocalRef(out);
        return result;
    }

    jobject makeMcuInfo(JNIEnv *env, jobject templateObj,
                        int mcuWidth, int mcuHeight, int imageWidth, int imageHeight) {

        jclass cls = env->GetObjectClass(templateObj);
        jmethodID ctor = env->GetMethodID(cls, "<init>", "(IIII)V");
        if (ctor == nullptr) {
            env->DeleteLocalRef(cls);
            return nullptr;
        }
        jobject result = env->NewObject(cls, ctor, mcuWidth, mcuHeight, imageWidth, imageHeight);
        env->DeleteLocalRef(cls);
        return result;
    }
}

extern "C" __attribute__((used, visibility("default"))) void _init(void) {} // UPX

extern "C" JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_options_transform_JPEGLosslessTransform_nativeCropLossless(
        JNIEnv *env, jobject /*thiz*/,
        jbyteArray inputBytes, jint x, jint y, jint w, jint h,
        jobject templateObj) {

    jsize inLen = env->GetArrayLength(inputBytes);
    jbyte *inData = env->GetByteArrayElements(inputBytes, nullptr);

    tjhandle xform = tjInitTransform();
    if (!xform) {
        env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
        return nullptr;
    }

    int width = 0, height = 0, jpegSubsamp = 0, jpegColorspace = 0;
    if (tjDecompressHeader3(xform,
                            reinterpret_cast<unsigned char *>(inData),
                            static_cast<unsigned long>(inLen),
                            &width, &height, &jpegSubsamp, &jpegColorspace) < 0) {
        tjDestroy(xform);
        env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
        return nullptr;
    }

    int mcuW = tjMCUWidth[jpegSubsamp];
    int mcuH = tjMCUHeight[jpegSubsamp];

    if (x < 0 || y < 0 || w <= 0 || h <= 0) {
        tjDestroy(xform);
        env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
        return nullptr;
    }

    int snappedX = (x / mcuW) * mcuW;
    int snappedY = (y / mcuH) * mcuH;
    int snappedW = ((x + w - snappedX) / mcuW) * mcuW;
    int snappedH = ((y + h - snappedY) / mcuH) * mcuH;
    if (snappedW < mcuW) snappedW = mcuW;
    if (snappedH < mcuH) snappedH = mcuH;
    if (snappedX + snappedW > width) snappedW = width - snappedX;
    if (snappedY + snappedH > height) snappedH = height - snappedY;

    tjtransform transform{};
    transform.op = TJXOP_NONE;
    transform.options = TJXOPT_CROP | TJXOPT_TRIM;
    transform.r.x = snappedX;
    transform.r.y = snappedY;
    transform.r.w = snappedW;
    transform.r.h = snappedH;

    unsigned char *outBuf = nullptr;
    unsigned long outSize = 0;

    int rc = tjTransform(xform,
                         reinterpret_cast<unsigned char *>(inData),
                         static_cast<unsigned long>(inLen),
                         1, &outBuf, &outSize, &transform, 0);

    env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
    tjDestroy(xform);

    if (rc < 0 || !outBuf) {
        __android_log_print(ANDROID_LOG_ERROR, "LosslessCrop",
                            "tjTransform crop failed: %s (crop %dx%d+%d+%d image %dx%d subsamp=%d)",
                            tjGetErrorStr(), snappedW, snappedH, snappedX, snappedY,
                            width, height, jpegSubsamp);
        if (outBuf) tjFree(outBuf);
        return nullptr;
    }

    jobject result = makeResult(env, templateObj, outBuf, outSize,
                                snappedX, snappedY, snappedW, snappedH);
    tjFree(outBuf);
    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_options_transform_JPEGLosslessTransform_nativeRotate90Lossless(
        JNIEnv *env, jobject /*thiz*/,
        jbyteArray inputBytes, jint quarterTurns, jboolean trim,
        jobject templateObj) {

    jsize inLen = env->GetArrayLength(inputBytes);
    jbyte *inData = env->GetByteArrayElements(inputBytes, nullptr);

    tjhandle xform = tjInitTransform();
    if (!xform) {
        env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
        return nullptr;
    }

    int op = TJXOP_NONE;
    switch (((quarterTurns % 4) + 4) % 4) {
        case 1:
            op = TJXOP_ROT90;
            break;
        case 2:
            op = TJXOP_ROT180;
            break;
        case 3:
            op = TJXOP_ROT270;
            break;
        default:
            op = TJXOP_NONE;
            break;
    }

    tjtransform transform{};
    transform.op = op;
    transform.options = trim ? TJXOPT_TRIM : 0;

    unsigned char *outBuf = nullptr;
    unsigned long outSize = 0;

    int rc = tjTransform(xform,
                         reinterpret_cast<unsigned char *>(inData),
                         static_cast<unsigned long>(inLen),
                         1, &outBuf, &outSize, &transform, 0);

    env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
    tjDestroy(xform);

    if (rc < 0 || !outBuf) {
        if (outBuf) tjFree(outBuf);
        return nullptr;
    }

    jobject result = makeResult(env, templateObj, outBuf, outSize, 0, 0, 0, 0);
    tjFree(outBuf);
    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_org_codeberg_editorie_options_transform_JPEGLosslessTransform_nativeGetMcuSize(
        JNIEnv *env, jobject /*thiz*/,
        jbyteArray inputBytes,
        jobject templateObj) {

    jsize inLen = env->GetArrayLength(inputBytes);
    jbyte *inData = env->GetByteArrayElements(inputBytes, nullptr);

    tjhandle handle = tjInitDecompress();
    int width = 0, height = 0, subsamp = 0, colorspace = 0;
    tjDecompressHeader3(handle,
                        reinterpret_cast<unsigned char *>(inData),
                        static_cast<unsigned long>(inLen),
                        &width, &height, &subsamp, &colorspace);

    env->ReleaseByteArrayElements(inputBytes, inData, JNI_ABORT);
    tjDestroy(handle);

    return makeMcuInfo(env, templateObj,
                       tjMCUWidth[subsamp], tjMCUHeight[subsamp], width, height);
}
