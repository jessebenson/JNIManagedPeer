/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Jesse Benson
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#pragma once

#include <jni.h>

namespace JNI {

    // Stores an auto ref-counted (global reference) jobject
    struct JObject
    {
        JObject();
        JObject(jobject object, bool releaseLocalRef = false);
        JObject(const JObject& object);
        JObject(JObject&& object);
        ~JObject();

        JObject& operator=(jobject object);
        JObject& operator=(const JObject& object);
        JObject& operator=(JObject&& object);

        jobject Object() const { return m_Object; }
        operator jobject() const { return m_Object; }

    protected:
        void AttachObject(JNIEnv* env, jobject object);
        void ReleaseObject(JNIEnv* env);

        void AttachLocalObject(JNIEnv* env, jobject object);

    private:
        jobject m_Object = nullptr;
    };


    // Stores an auto ref-counted (global reference) jclass
    struct JClass : public JObject
    {
        explicit JClass(const char* className);
        JClass(jclass clazz) : JObject(clazz) { }
        JClass(const JClass& clazz) : JObject(clazz) { }
        JClass(JClass&& clazz) : JObject(clazz) { }
        ~JClass();

        JClass& operator=(jclass clazz)
        {
            JObject::operator=(clazz);
            return *this;
        }

        JClass& operator=(const JClass& clazz)
        {
            JObject::operator=(clazz);
            return *this;
        }

        JClass& operator=(JClass&& clazz)
        {
            JObject::operator=(clazz);
            return *this;
        }

        operator jclass() const { return (jclass) Object(); }
        jclass Class() const { return (jclass) Object(); }
    };


    // Stores an auto ref-counted (global reference) jstring
    class JString : public JObject
    {
    public:
        JString(jstring string, bool removeLocalRef = false);
        JString(const JString& string) : JObject(string) { }
        JString(JString&& string) : JObject(string) { }
        JString(const char* content);
        JString(const wchar_t* content);
        ~JString();

        JString& operator=(jstring string)
        {
            Clear();
            JObject::operator=(string);
            return *this;
        }

        JString& operator=(const JString& string)
        {
            Clear();
            JObject::operator=(string);
            return *this;
        }

        JString& operator=(JString&& string)
        {
            Clear();
            JObject::operator=(string);
            return *this;
        }

        const char* GetUTFString() const;
        int GetUTFLength() const;

        const wchar_t* GetStringChars() const;
        int GetLength() const;

        operator jstring() const { return (jstring) Object(); };
        jstring String() const { return (jstring) Object(); }

    private:
        void Clear();

        const char* m_pString = nullptr;
        const wchar_t* m_pWString = nullptr;
    };


    // Base class for all auto-generated "managed peer" classes.
    class ManagedPeer
    {
    public:
        // Constructor with a Java object to be able to invoke instance methods.
        ManagedPeer();
        explicit ManagedPeer(jobject object);
        ~ManagedPeer();

        ManagedPeer& operator=(jobject obj);

        operator jobject () const { return m_Object; }
        jobject Object() const { return m_Object; }

        // Helper to get the JNI environment for invoking Java methods
        static JNIEnv& Env();

    private:
        JObject m_Object;
    };


    // Store the Java virtual machine for general use.  Should be set in JNI_OnLoad.
    void STDMETHODCALLTYPE SetJVM(JavaVM* jvm);
    JavaVM* STDMETHODCALLTYPE GetJVM();

} // namespace JNI
