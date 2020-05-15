
#include <jni.h>
#include <sodium/randombytes.h>
#include <sodium/core.h>

JNIEXPORT void JNICALL Java_com_waz_utils_crypto_RandomBytes_randomBytes(JNIEnv *jenv, jobject obj, jbyteArray jarr, jint jcount) {
    unsigned char* buffer = (unsigned char *) (*jenv)->GetByteArrayElements(jenv, jarr, 0);
    size_t count = (size_t) jcount;
    randombytes_buf(buffer, count);
    (*jenv)->ReleaseByteArrayElements(jenv, jarr, (jbyte *) buffer, 0);
}

jint JNI_OnLoad(JavaVM * vm, void* reserved) {
  if (sodium_init() < 0) {
    return -1;
  }

  return JNI_VERSION_1_6;
}
