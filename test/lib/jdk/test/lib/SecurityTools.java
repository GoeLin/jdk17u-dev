/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * Run security tools (including jarsigner and keytool) in a new process.
 * The en_US locale is always used so a test can always match output to
 * English text.  An argument can be a normal string,
 * {@code -Jvm-options}, {@code $sysProp} or {@code -J$sysProp}.
 */
public class SecurityTools {

    /**
     * The response file name for keytool. Use {@link #setResponse} to
     * create one. Do NOT manipulate it directly.
     */
    public static final String RESPONSE_FILE = "security_tools_response.txt";

    private SecurityTools() {}

    public static ProcessBuilder getProcessBuilder(String tool, List<String> args) {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK(tool)
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US");
        for (String arg : args) {
            if (arg.startsWith("-J")) {
                String jarg = arg.substring(2);
                if (jarg.length() > 1 && jarg.charAt(0) == '$') {
                    launcher.addVMArg(System.getProperty(jarg.substring(1)));
                } else {
                    launcher.addVMArg(jarg);
                }
            } else if (Platform.isWindows() && arg.isEmpty()) {
                // JDK-6518827: special handling for empty argument on Windows
                launcher.addToolArg("\"\"");
            } else if (arg.length() > 1 && arg.charAt(0) == '$') {
                launcher.addToolArg(System.getProperty(arg.substring(1)));
            } else {
                launcher.addToolArg(arg);
            }
        }
        return new ProcessBuilder(launcher.getCommand());
    }

    /**
     * Runs keytool.
     *
     * @param args arguments to keytool
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer keytool(List<String> args)
            throws Exception {

        ProcessBuilder pb = getProcessBuilder("keytool", args);

        Path p = Paths.get(RESPONSE_FILE);
        if (!Files.exists(p)) {
            Files.createFile(p);
        }
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(RESPONSE_FILE)));

        try {
            return execute(pb);
        } finally {
            Files.delete(p);
        }
    }

    /**
     * Runs keytool.
     *
     * @param args arguments to keytool in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer keytool(String args) throws Exception {
        return keytool(makeList(args));
    }

    /**
     * Runs keytool.
     *
     * @param args arguments to keytool
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer keytool(String... args) throws Exception {
        return keytool(List.of(args));
    }


    /**
     * Sets the responses (user input) for keytool.
     * <p>
     * For example, if keytool requires entering a password twice, call
     * {@code setResponse("password", "password")}. Do NOT append a newline
     * character to each response. If there are useless responses at the end,
     * they will be discarded silently. If there are less responses than
     * necessary, keytool will read EOF. The responses will be written into
     * {@linkplain #RESPONSE_FILE a local file} and will only be used by the
     * next keytool run. After the run, the file is removed. Calling this
     * method will always overwrite the previous response file (if exists).
     *
     * @param responses response to keytool
     * @throws IOException if there is an error
     */
    public static void setResponse(String... responses) throws IOException {
        String text;
        if (responses.length > 0) {
            text = Stream.of(responses).collect(
                    Collectors.joining("\n", "", "\n"));
        } else {
            text = "";
        }
        Files.write(Paths.get(RESPONSE_FILE), text.getBytes());
    }

    /**
     * Runs jarsigner.
     *
     * @param args arguments to jarsigner
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jarsigner(List<String> args)
            throws Exception {
        return execute(getProcessBuilder("jarsigner", args));
    }

    private static OutputAnalyzer execute(ProcessBuilder pb) throws Exception {
        try {
            OutputAnalyzer oa = ProcessTools.executeCommand(pb);
            System.out.println("Exit value: " + oa.getExitValue());
            return oa;
        } catch (Throwable t) {
            if (t instanceof Exception) {
                throw (Exception) t;
            } else {
                throw new Exception(t);
            }
        }
    }

    /**
     * Runs jarsigner.
     *
     * @param args arguments to jarsigner in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jarsigner(String args) throws Exception {

        return jarsigner(makeList(args));
    }

    /**
     * Runs jarsigner.
     *
     * @param args arguments to jarsigner
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jarsigner(String... args) throws Exception {
        return jarsigner(List.of(args));
    }

    /**
     * Runs ktab.
     *
     * @param args arguments to ktab in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer ktab(String args) throws Exception {
        return execute(getProcessBuilder("ktab", makeList(args)));
    }

    /**
     * Runs klist.
     *
     * @param args arguments to klist in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer klist(String args) throws Exception {
        return execute(getProcessBuilder("klist", makeList(args)));
    }

    /**
     * Runs kinit.
     *
     * @param args arguments to kinit in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer kinit(String args) throws Exception {
        return execute(getProcessBuilder("kinit", makeList(args)));
    }

    /**
     * Runs jar.
     *
     * @param args arguments to jar in the list.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jar(final List<String> args) throws Exception {
        return execute(getProcessBuilder("jar", args));
    }

    /**
     * Runs jar.
     *
     * @param args arguments to jar in a single string. The string is
     *             converted to be List with makeList.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jar(final String args) throws Exception {
        return  jar(makeList(args));
    }

    /**
     * Runs jar.
     *
     * @param args arguments to jar in multiple strings.
     *             Converted to be a List with List.of.
     * @return an {@link OutputAnalyzer} object
     * @throws Exception if there is an error
     */
    public static OutputAnalyzer jar(final String... args) throws Exception {
        return jar(List.of(args));
    }

    /**
     * Split a line to a list of string. All whitespaces are treated as
     * delimiters unless quoted between ` and `.
     *
     * @param line the input
     * @return the list
     */
    public static List<String> makeList(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inBackTick = false;
        for (char c : line.toCharArray()) {
            if (inBackTick) {
                if (c == '`') {
                    result.add(sb.toString());
                    sb.setLength(0);
                    inBackTick = false;
                } else {
                    sb.append(c);
                }
            } else {
                if (sb.length() == 0 && c == '`') {
                    // Allow ` inside a string
                    inBackTick = true;
                } else {
                    if (Character.isWhitespace(c)) {
                        if (sb.length() != 0) {
                            result.add(sb.toString());
                            sb.setLength(0);
                        }
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        if (sb.length() != 0) {
            result.add(sb.toString());
        }
        return result;
    }

    // Create a temporary keychain in macOS and use it. The original
    // keychains will be restored when the object is closed.
    public static class TemporaryKeychain implements Closeable {
        // name of new keychain
        private final String newChain;
        // names of the original keychains
        private final List<String> oldChains;

        public TemporaryKeychain(String name) {
            Path p = Path.of(name + ".keychain-db");
            newChain = p.toAbsolutePath().toString();
            try {
                oldChains = ProcessTools.executeProcess("security", "list-keychains")
                        .shouldHaveExitValue(0)
                        .getStdout()
                        .lines()
                        .map(String::trim)
                        .map(x -> x.startsWith("\"") ? x.substring(1, x.length() - 1) : x)
                        .collect(Collectors.toList());
                if (!Files.exists(p)) {
                    ProcessTools.executeProcess("security", "create-keychain", "-p", "changeit", newChain)
                            .shouldHaveExitValue(0);
                }
                ProcessTools.executeProcess("security", "unlock-keychain", "-p", "changeit", newChain)
                        .shouldHaveExitValue(0);
                ProcessTools.executeProcess("security", "list-keychains", "-s", newChain)
                        .shouldHaveExitValue(0);
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }

        public String chain() {
            return newChain;
        }

        @Override
        public void close() throws IOException {
            List<String> cmds = new ArrayList<>();
            cmds.addAll(List.of("security", "list-keychains", "-s"));
            cmds.addAll(oldChains);
            try {
                ProcessTools.executeProcess(cmds.toArray(new String[0]))
                        .shouldHaveExitValue(0);
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }
    }
}

