package cd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

	public static String readAll(Reader ubReader) throws IOException {
		BufferedReader bReader = new BufferedReader(ubReader);
		StringBuilder sb = new StringBuilder();

		while (true) {
			int ch = bReader.read();
			if (ch == -1) {
				bReader.close();
				return sb.toString();
			}
			sb.append((char) ch);
		}
	}

	public static String read(File file) throws IOException {
		return readAll(new FileReader(file));
	}

	public static void write(File file, String text) throws IOException {
		FileWriter writer = new FileWriter(file);
		writer.write(text);
		writer.close();
	}

	public static String runCommand(File dir, String[] command,
			String[] substs, String input, boolean detectError)
			throws IOException {
		// Substitute the substitution strings $0, $1, etc
		String newCommand[] = new String[command.length];
		for (int i = 0; i < command.length; i++) {
			String newItem = command[i];
			for (int j = 0; j < substs.length; j++)
				newItem = newItem.replace("$" + j, substs[j]);
			newCommand[i] = newItem;
		}

		// Run the command in the specified directory
		ProcessBuilder pb = new ProcessBuilder(newCommand);
		pb.directory(dir);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		if (input != null && !input.equals("")) {
			OutputStreamWriter osw = new OutputStreamWriter(p.getOutputStream());
			osw.write(input);
			osw.close();
		}
		String result = readAll(new InputStreamReader(p.getInputStream()));

		if (detectError) {
			int err;
			try {
				err = p.waitFor();

				// hack: same as ReferenceServer returns when
				// a dynamic error occurs running the interpreter
				if (err != 0)
					return "Error: " + err + "\n";
			} catch (InterruptedException e) {
			}
		}
		return result;
	}

	/**
	 * Finds all .javali under directory {@code testDir}, adding File objects
	 * into {@code result} for each one.
	 */
	public static void findFiles(File testDir, List<Object[]> result) {
		for (File testFile : testDir.listFiles()) {
			if (testFile.getName().endsWith(".javali"))
				result.add(new Object[] { testFile });
			else if (testFile.isDirectory())
				findFiles(testFile, result);
		}
	}

	/** Finds all .javali under directory {@code testDir} and returns them. */
	public static List<File> findFiles(File testDir) {
		List<File> result = new ArrayList<File>();
		for (File testFile : testDir.listFiles()) {
			if (testFile.getName().endsWith(".javali"))
				result.add(testFile);
			else if (testFile.isDirectory())
				result.addAll(findFiles(testFile));
		}
		return result;
	}
}
