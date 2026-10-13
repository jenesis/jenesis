#include "sample_adder_Adder.h"

JNIEXPORT jint JNICALL Java_sample_adder_Adder_add(JNIEnv *env, jclass type, jint a, jint b) {
    return a + b;
}
