#pragma once

#include <jni.h>

namespace JNI {

	// Base class for all auto-generated "managed peer" classes.
	class ManagedPeer
	{
	public:
		// Default constructor with no Java object when only needing to call static methods.
		ManagedPeer();

		// Constructor with a Java object to be able to invoke instance methods.
		explicit ManagedPeer(jobject object);

		~ManagedPeer();

		jobject Object() const { return m_Object; }

		// Helper to get the JNI environment for invoking Java methods
		static JNIEnv& Env();

	private:
		jobject m_Object;
	};


	// Helper class to store an auto ref-counted jclass
	struct JClass
	{
		JClass(const char* className);
		~JClass();

		operator jclass() const { return m_Class; }

	private:
		jclass m_Class;
	};


	// Store the Java virtual machine for general use.  Should be set in JNI_OnLoad.
	static void SetJVM(JavaVM* jvm);
	static JavaVM* GetJVM();

} // namespace JNI
