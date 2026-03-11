package org.agmas.noellesroles.packet;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import javax.management.ObjectName;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class URLBlockTransformer implements ClassFileTransformer {

    public static boolean transformed = false;
    public static synchronized void tryTransform() {
        if (transformed) return;

        try {
            // 方法1: 使用Java的Instrumentation API
            Class<?> agentClass = Class.forName("java.lang.instrument.Instrumentation");

            // 尝试从Fabric的类加载器中获取Instrumentation
            Instrumentation instrumentation = getInstrumentation();

            if (instrumentation != null) {
                System.out.println("[NoellesRoles] Found Instrumentation, adding transformer");

                URLBlockTransformer transformer = new URLBlockTransformer();
                instrumentation.addTransformer(transformer, true);

                // 重新转换已加载的URL类
                Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
                for (Class<?> clazz : loadedClasses) {
                    if ("java.net.URL".equals(clazz.getName())) {
                        try {
                            instrumentation.retransformClasses(clazz);
                            System.out.println("[NoellesRoles] Successfully transformed java.net.URL");
                            transformed = true;
                            break;
                        } catch (Exception e) {
                            System.err.println("[NoellesRoles] Failed to retransform URL: " + e.getMessage());
                        }
                    }
                }
            } else {
                System.out.println("[NoellesRoles] Could not get Instrumentation, trying alternative approach");
                transformViaClassLoader();
            }

        } catch (Exception e) {
            System.err.println("[NoellesRoles] Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Instrumentation getInstrumentation() {
        try {
            // 尝试通过反射获取Instrumentation
            Class<?> agentClass = Class.forName("java.lang.instrument.Instrumentation");

            // 方法1: 检查是否有已存在的Instrumentation
            Field[] fields = ClassLoader.class.getDeclaredFields();
            for (Field field : fields) {
                if (agentClass.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(Thread.currentThread().getContextClassLoader());
                    if (value != null) {
                        return (Instrumentation) value;
                    }
                }
            }

            // 方法2: 通过ManagementFactory获取（如果可用）
            Class<?> managementFactory = Class.forName("java.lang.management.ManagementFactory");
            Method getPlatformMBeanServer = managementFactory.getMethod("getPlatformMBeanServer");
            Object mbs = getPlatformMBeanServer.invoke(null);

            // 尝试从MBeanServer获取
            Class<?> mBeanServerClass = Class.forName("javax.management.MBeanServer");
            Method getAttribute = mBeanServerClass.getMethod("getAttribute",
                    javax.management.ObjectName.class, String.class);

            Class<?> objectNameClass = Class.forName("javax.management.ObjectName");
            ObjectName name = (ObjectName) objectNameClass.getConstructor(String.class)
                    .newInstance("java.lang:type=Management");

            Object instrumentationObj = getAttribute.invoke(mbs, name, "Instrumentation");
            if (instrumentationObj != null && agentClass.isInstance(instrumentationObj)) {
                return (Instrumentation) instrumentationObj;
            }

        } catch (Exception e) {
            // 忽略异常，尝试其他方法
        }
        return null;
    }

    public static void transformViaClassLoader() {
        try {
            // 方法2: 通过自定义ClassLoader进行转换
            System.out.println("[NoellesRoles] Attempting to transform via ClassLoader hack");

            // 获取当前ClassLoader
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            // 创建一个自定义的ClassLoader包装器
            ClassLoader transformerLoader = new ClassLoader(cl) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    // 拦截URL类的加载
                    if ("java.net.URL".equals(name)) {
                        synchronized (getClassLoadingLock(name)) {
                            // 先检查是否已加载
                            Class<?> c = findLoadedClass(name);
                            if (c == null) {
                                try {
                                    // 从原始JAR加载类字节
                                    String resourceName = name.replace('.', '/') + ".class";
                                    java.io.InputStream is = getParent().getResourceAsStream(resourceName);

                                    if (is != null) {
                                        byte[] bytes = is.readAllBytes();
                                        is.close();

                                        // 应用转换
                                        URLBlockTransformer transformer = new URLBlockTransformer();
                                        byte[] transformed = transformer.transform(
                                                this, name.replace('.', '/'),
                                                null, null, bytes);

                                        // 定义转换后的类
                                        c = defineClass(name, transformed, 0, transformed.length);
                                        System.out.println("[NoellesRoles] Successfully transformed URL via ClassLoader");
                                    }
                                } catch (Exception e) {
                                    throw new ClassNotFoundException(name, e);
                                }
                            }

                            if (resolve) {
                                resolveClass(c);
                            }
                            return c;
                        }
                    }

                    // 其他类正常加载
                    return super.loadClass(name, resolve);
                }
            };

            // 设置当前线程的ClassLoader
            Thread.currentThread().setContextClassLoader(transformerLoader);

        } catch (Exception e) {
            System.err.println("[NoellesRoles] ClassLoader transformation failed: " + e.getMessage());
        }
    }
    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {

        // 只处理 java.net.URL 类
        if (!"java/net/URL".equals(className)) {
            return classfileBuffer;
        }

        try {
            // 可选：保存原始类文件用于调试
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                saveClassFile(className + "_original.class", classfileBuffer);
            }

            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new URLClassVisitor(Opcodes.ASM9, cw);
            cr.accept(cv, 0);

            byte[] transformed = cw.toByteArray();

            // 可选：保存转换后的类文件用于调试
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                saveClassFile(className + "_transformed.class", transformed);
            }

            return transformed;
        } catch (Exception e) {
            e.printStackTrace();
            return classfileBuffer;
        }
    }

    private void saveClassFile(String filename, byte[] data) {
        try {
            File debugDir = new File("asm_debug");
            if (!debugDir.exists()) debugDir.mkdirs();

            try (FileOutputStream fos = new FileOutputStream(new File(debugDir, filename))) {
                fos.write(data);
            }
        } catch (Exception e) {
            // 忽略调试文件保存错误
        }
    }

    private static class URLClassVisitor extends ClassVisitor {

        public URLClassVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            // 拦截 openConnection() 方法
            if ("openConnection".equals(name) &&
                    "()Ljava/net/URLConnection;".equals(descriptor)) {
                return new URLMethodVisitor(api, mv, access, name, descriptor);
            }

            // 也拦截带参数的 openConnection 方法
            if ("openConnection".equals(name) &&
                    "(Ljava/net/Proxy;)Ljava/net/URLConnection;".equals(descriptor)) {
                return new URLMethodVisitor(api, mv, access, name, descriptor);
            }

            return mv;
        }
    }

    private static class URLMethodVisitor extends AdviceAdapter {

        private final String methodDesc;

        protected URLMethodVisitor(int api, MethodVisitor mv, int access,
                                   String name, String desc) {
            super(api, mv, access, name, desc);
            this.methodDesc = desc;
        }

        @Override
        protected void onMethodEnter() {
            // 在方法开始时插入检查
            visitVarInsn(ALOAD, 0); // 加载 this (URL 对象)
            visitMethodInsn(INVOKEVIRTUAL, "java/net/URL",
                    "getHost", "()Ljava/lang/String;", false);

            // 检查是否包含 "github"
            visitLdcInsn("github");
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String",
                    "contains", "(Ljava/lang/CharSequence;)Z", false);

            Label continueLabel = new Label();
            visitJumpInsn(IFEQ, continueLabel); // 如果不包含，继续执行

            // 如果包含 "github"，返回 null
            visitInsn(ACONST_NULL);
            visitInsn(ARETURN);

            visitLabel(continueLabel);
            visitFrame(F_SAME, 0, null, 0, null);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 确保有足够的栈空间
            super.visitMaxs(Math.max(maxStack, 3), maxLocals);
        }
    }
}