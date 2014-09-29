#include <precomp.h>
#include "JNIManagedPeer.h"

namespace JNI {

	static JavaVM* s_JVM = nullptr;

	void SetJVM(JavaVM* jvm)
	{
		s_JVM = jvm;
	}

	JavaVM* GetJVM()
	{
		return s_JVM;
	}

	static JNIEnv* GetEnvironment()
	{
		JNIEnv* env = nullptr;
		if (s_JVM != nullptr)
			s_JVM->AttachCurrentThread(&env, nullptr);
		return *env;
	}


	JNIManagedPeer::JNIManagedPeer()
		: m_Object(nullptr)
	{
	}

	JNIManagedPeer::JNIManagedPeer(jobject object)
		: m_Object(object)
	{
		Env().NewGlobalRef(object);
	}

	JNIManagedPeer::~JNIManagedPeer()
	{
		if (m_Object != nullptr)
			Env().DeleteGlobalRef(m_Object);
	}

	JNIEnv& JNIManagedPeer::Env()
	{
		return *GetEnvironment();
	}


	JClass::JClass(const char* className)
	{
		JNIEnv* env = GetEnvironment();
		jclass clazz = env->FindClass(className);
		m_Class = env->NewGlobalRef(jclazz);
		env->DeleteLocalRef(clazz);		
	}

	JClass::~JClass()
	{
		GetEnvironment()->DeleteGlobalRef(m_Class);
	}

} // namespace JNI
