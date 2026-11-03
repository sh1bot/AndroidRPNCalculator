// SPDX-License-Identifier: Apache-2.0
/** Portable javac entry point for JDK installations without a javac launcher. */
public class Compile {
    public static void main(String[] args) {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Install a JDK, not just a JRE");
        System.exit(compiler.run(null, System.out, System.err, args));
    }
}
