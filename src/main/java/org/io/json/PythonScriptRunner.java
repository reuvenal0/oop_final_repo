package org.io.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a Python script as an external process.
 *
 * Responsibility:
 * 1) Build the command: python <script>
 * 2) Set a correct working directory (so relative file outputs go to the expected folder)
 * 3) Wait until Python finishes
 * 4) Fail fast if Python returns a non-zero exit code
 *
 * This class does NOT parse JSON and does NOT contain UI logic.
 */
public final class PythonScriptRunner {

    private final String pythonExecutable;   // e.g. "python" or "python3"
    private final Path scriptPath;           // absolute path to the script file
    private final Path workingDirectory;     // absolute path to the script's parent folder

    /**
     * Convenience constructor: uses "python" as the executable.
     *
     * @param scriptPath Path to the Python file, can be relative or absolute.
     */
    public PythonScriptRunner(Path scriptPath) {
        this("python", scriptPath);
    }

    /**
     * main.org.app.Main constructor.
     *
     * @param pythonExecutable "python", "python3", or a full path to a venv python.exe
     * @param scriptPath Path to the Python script (relative or absolute)
     */
    public PythonScriptRunner(String pythonExecutable, Path scriptPath) {
        if (pythonExecutable == null || pythonExecutable.isBlank()) {
            throw new IllegalArgumentException("pythonExecutable must not be null/blank");
        }
        if (scriptPath == null) {
            throw new IllegalArgumentException("scriptPath must not be null");
        }

        // Convert the script path to an absolute path.
        // This prevents issues when IntelliJ or the OS changes the working directory.
        Path absScript = scriptPath.toAbsolutePath().normalize();

        // Fail early with a clear message if the file does not exist.
        if (!Files.exists(absScript)) {
            throw new IllegalArgumentException("Python script file does not exist: " + absScript);
        }

        this.pythonExecutable = pythonExecutable;
        this.scriptPath = absScript;

        // The working directory is the folder where embedder.py lives.
        // This is IMPORTANT because your Python script writes JSON files "in the same folder".
        this.workingDirectory = absScript.getParent();

        if (this.workingDirectory == null) {
            // This can happen only in strange edge cases (e.g. scriptPath has no parent).
            throw new IllegalStateException("Cannot determine working directory for script: " + absScript);
        }
    }

    /**
     * Runs the python script and blocks until it finishes.
     *
     * @return exit code (0 means success)
     */
    public int run() throws IOException, InterruptedException {
        // Build the command: python <absolute_path_to_script>
        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                scriptPath.toString()
        );

        // Run the Python process from the script folder.
        // This ensures the output JSON files are created in the same folder.
        pb.directory(workingDirectory.toFile());

        // Merge stderr into stdout to avoid deadlocks (in case Python prints a lot of errors).
        pb.redirectErrorStream(true);

        // Show Python output in the Java console (very helpful for debugging).
        // You will see the same logs you saw when running from PowerShell.
        pb.inheritIO();

        // Start the process
        Process process = pb.start();

        // Wait for it to finish
        int exitCode = process.waitFor();

        // Non-zero means Python failed (e.g., file not found, exception, missing packages, etc.)
        if (exitCode != 0) {
            throw new IOException("Python script failed (exit code=" + exitCode + "): " + scriptPath);
        }

        return exitCode;
    }

    // Getters (optional, but useful for debugging/logging/tests)

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public Path getScriptPath() {
        return scriptPath;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }
}