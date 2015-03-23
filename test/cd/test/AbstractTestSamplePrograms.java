package cd.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import cd.Config;
import cd.Main;
import cd.debug.AstDump;
import cd.exceptions.AssemblyFailedException;
import cd.exceptions.ParseFailure;
import cd.exceptions.SemanticFailure;
import cd.ir.Ast.ClassDecl;
import cd.util.FileUtil;

abstract public class AbstractTestSamplePrograms {

	public File file, sfile, binfile, infile;
	public File parserreffile, semanticreffile, execreffile, cfgreffile, optreffile;
	public File errfile;
	public Main main;

	public void assertEquals(String phase, String exp, String act) {
		act = act.replace("\r\n", "\n"); // for windows machines
		if (!exp.equals(act)) {
			warnAboutDiff(phase, exp, act);
		}
	}
	
	/**
	 * Compare the output of two executions while ignoring
	 * small differences due to floating point errors.
	 * E.g. outputs "1.23456" and "1.23455" are OK even though 
	 * they are slightly different. 
	 * 
	 */
	public void assertEqualOutput(String phase, String exp, String act) {
		act = act.replace("\r\n", "\n"); // for windows machines
		if (!exp.equals(act)) {
			String[] expLines = exp.split("\n");
			String[] actLines = act.split("\n");
			if (expLines.length != actLines.length) warnAboutDiff(phase, exp, act);
			for (int lineNb = 0; lineNb < expLines.length; lineNb++) {
				String expLine = expLines[lineNb];
				String actLine = actLines[lineNb];
				// assumption: all output w/ a dot is a floating point nb.
				if (expLine.contains(".") && actLine.contains(".")) {  
					// allow rounding differences when comparing floating points
					// (known bug: this doesn't work if there are two floats on a single output line)
					float expFloat = Float.valueOf(expLine);
					float actFloat = Float.valueOf(actLine);
					if (Math.abs(expFloat - actFloat) > 0.001) warnAboutDiff(phase, exp, act);
				} else {
					if (!expLine.equals(actLine)) warnAboutDiff(phase, exp, act);
				}
			}
		}
	}

	private void warnAboutDiff(String phase, String exp, String act) {
		try {
			PrintStream err = new PrintStream(errfile);
			err.println("Debug information for file: " + this.file);
			err.println(this.main.debug.toString());
			err.println(String.format(
					"Phase %s failed because we expected to see:", phase));
			err.println(exp);
			err.println("But we actually saw:");
			err.println(act);
			err.println("The difference is:");
			err.println(Diff.computeDiff(exp, act));
			err.close();
		} catch (FileNotFoundException exc) {
			System.err.println("Unable to write debug output to " + errfile
					+ ":");
			exc.printStackTrace();
		}
		Assert.assertEquals(
				String.format("Phase %s for %s failed!", phase,
						file.getPath()), exp, act);
	}
	
	public static int counter = 0;

	@Test
	public void test() throws Throwable {
		System.err.println("[" + counter++ + " = " + file + "]");

		// ignore 64-bit-only tests when running 32-bit Java
		if (new File(file.getAbsolutePath()+".64bitonly").exists() &&
				Integer.valueOf(System.getProperty("sun.arch.data.model")) == 32) {
			System.err.println("--> Ignoring test because it's 64-bit-only");
		} else {
			boolean hasWellDefinedOutput = !new File(file.getAbsolutePath()+".undefinedOutput").exists();
			
			try {
				// Load the input and reference results:
				// Note: this may use the network if no .ref files exist.
	
				// Delete intermediate files from previous runs:
				if (sfile.exists())
					sfile.delete();
				if (binfile.exists())
					binfile.delete();
	
				// Parse the file and check that the generated AST is correct,
				// or if the parser failed that the correct message was generated:
				List<ClassDecl> astRoots = testParser();
				if (astRoots != null) {
					{
						// Run the semantic check and check that errors
						// are detected correctly, etc.
						boolean passedSemanticAnalysis = testSemanticAnalyzer(astRoots);
						
						if (passedSemanticAnalysis) {
							boolean passedCodeGen = testCodeGenerator(astRoots, hasWellDefinedOutput);
						}
					}
				}
			} catch (org.junit.ComparisonFailure cf) {
				throw cf;
			} catch (Throwable e) {
				PrintStream err = new PrintStream(errfile);
				err.println("Debug information for file: " + this.file);
				err.println(this.main.debug.toString());
				err.println("Test failed because an exception was thrown:");
				err.println("    " + e.getLocalizedMessage());
				err.println("Stack trace:");
				e.printStackTrace(err);
				throw e;
			}
	
			// if we get here, then the test passed, so delete the errfile:
			// (which has been accumulating debug output etc)
			if (errfile.exists())
				errfile.delete();
		}
	}

	/** Run the parser and compare the output against the reference results */
	public List<ClassDecl> testParser() throws Exception {
		String parserRef = findParserRef();
		List<ClassDecl> astRoots = null;
		String parserOut;
		boolean parserDebug;

		// CUP's debug output is NOT relevant to this assignment.
		// Change to TRUE if you'd like to see it for some reason.
		parserDebug = false;
		try {
			astRoots = main.parse(file.getAbsolutePath(), new FileReader(
					this.file), parserDebug);
			parserOut = AstDump.toString(astRoots);
		} catch (ParseFailure pf) {
			// Parse errors are ok too.
			main.debug("");
			main.debug("");
			main.debug("%s", pf.toString());
			parserOut = Reference.PARSE_FAILURE;
		}

		// Now that the 2nd assignment is over, we don't
		// do a detailed comparison of the AST, just check
		// whether the parse succeeded or failed.
		if (parserOut.equals(Reference.PARSE_FAILURE)
				|| parserRef.equals(Reference.PARSE_FAILURE))
			assertEquals("parser", parserRef, parserOut);
		return astRoots;
	}

	public boolean testSemanticAnalyzer(List<ClassDecl> astRoots)
			throws IOException {
		String semanticRef = findSemanticRef();

		boolean passed;
		String result;
		try {
			main.semanticCheck(astRoots);
			result = "OK";
			passed = true;
		} catch (SemanticFailure sf) {
			result = sf.cause.name();
			main.debug("Error message: %s", sf.getLocalizedMessage());
			passed = false;
		}

		assertEquals("semantic", semanticRef, result);
		return passed;
	}
	
	/**
	 * Run the code generator, assemble the resulting .s file, and (if the output
	 * is well-defined) compare against the expected output.
	 */
	public boolean testCodeGenerator(List<ClassDecl> astRoots, boolean hasWellDefinedOutput)
			throws IOException {
		// Determine the input and expected output.
		String inFile = (infile.exists() ? FileUtil.read(infile) : "");
		String execRef = findExecRef(inFile);

		// Run the code generator:
		FileWriter fw = new FileWriter(this.sfile);
		main.generateCode(astRoots, fw);
		fw.close();

		// At this point, we have generated a .s file and we have to compile
		// it to a binary file. We need to call out to GCC or something
		// to do this.
		String asmOutput = FileUtil.runCommand(
				Config.ASM_DIR,
				Config.ASM,
				new String[] { binfile.getAbsolutePath(),
						sfile.getAbsolutePath() }, null, false);

		// To check if gcc succeeded, check if the binary file exists.
		// We could use the return code instead, but this seems more
		// portable to other compilers / make systems.
		if (!binfile.exists())
			throw new AssemblyFailedException(asmOutput);

		// Execute the binary file, providing input if relevant, and
		// capturing the output. Check the error code so see if the
		// code signaled dynamic errors.
		String execOut = FileUtil.runCommand(new File("."),
				new String[] { binfile.getAbsolutePath() }, new String[] {},
				inFile, true);

		// Compute the output to what we expected to see.
		if (execRef.equals(execOut))
			return true;
		if (hasWellDefinedOutput) assertEqualOutput("exec", execRef, execOut);
		return false;
	}

	public String findParserRef() throws IOException {
		// Check for a .ref file
		if (parserreffile.exists() && parserreffile.lastModified() > file.lastModified()) {
			return FileUtil.read(parserreffile);
		}

		// If no file exists, contact reference server
		String res;
		Reference ref = openClient();
		try {
			res = ref.parserReference(FileUtil.read(this.file));
		} catch (Throwable e) {
			return fragmentBug(e);
		}
		FileUtil.write(parserreffile, res);
		return res;
	}

	public String findSemanticRef() throws IOException {
		// Semantic ref file is a little different. It consists
		// of 2 lines, but only the first line is significant.
		// The second one contains additional information that we log
		// to the debug file.

		// Read in the result
		String res;
		if (semanticreffile.exists() && semanticreffile.lastModified() > file.lastModified())
			res = FileUtil.read(semanticreffile);
		else {
			Reference ref = openClient();
			try {
				res = ref.semanticReference(FileUtil.read(this.file));
			} catch (Throwable e) {
				return fragmentBug(e);
			}
			FileUtil.write(semanticreffile, res);
		}

		// Extract the first line: there should always be multiple lines,
		// but someone may have tinkered with the file or something
		if (res.contains("\n")) {
			int newline = res.indexOf("\n");
			String cause = res.substring(newline + 1);
			if (!cause.equals("") && !cause.equals("\n"))
				main.debug("Error message from reference is: %s", cause);
			return res.substring(0, newline); // 1st line
		} else {
			return res;
		}
	}

	public String findExecRef(String inputText) throws IOException {
		// Check for a .ref file
		if (execreffile.exists() && execreffile.lastModified() > file.lastModified()) {
			return FileUtil.read(execreffile);
		}

		// If no file exists, use the interpreter to generate one.
		Reference ref = openClient();
		String res;
		try {
			res = ref.execReference(FileUtil.read(this.file), inputText);
		} catch (Throwable e) {
			return fragmentBug(e);
		}
		FileUtil.write(execreffile, res);
		return res;
	}

	private String findOptimizerRef(String inputText) throws IOException {
		// Check for a .ref file
		if (optreffile.exists() && optreffile.lastModified() > file.lastModified()) {
			return FileUtil.read(optreffile);
		}

		// If no file exists, use the interpreter to generate one.
		Reference ref = openClient();
		String res;
		try {
			res = ref.optReference(FileUtil.read(this.file), inputText);
		} catch (Throwable e) {
			return fragmentBug(e);
		}
		FileUtil.write(optreffile, res);
		return res;
	}

	private String fragmentBug(Throwable e) {
		String res = String.format("** BUG IN REFERENCE SOLUTION: %s **",
				e.toString());
		main.debug(res);
		e.printStackTrace(new PrintWriter(main.debug));
		return res;
	}

	/**
	 * Connects to {@code niko.inf.ethz.ch} and returns a Reference instance.
	 * This uses Java RMI to obtain the expected answer for various stages.
	 * Generally, this is only invoked if no appropriate .ref file is found.
	 */
	public static Reference openClient() {
		Registry registry;
		try {
			registry = LocateRegistry.getRegistry("beholder.inf.ethz.ch");
			Reference ref = (Reference) registry.lookup(Reference.class
					.getName());
			return ref;
		} catch (RemoteException e) {
			// No network connectivity or server not running?
			throw new RuntimeException(e);
		} catch (NotBoundException e) {
			// Server not running on niko.inf.ethz.ch?
			throw new RuntimeException(e);
		}
	}

	
}
