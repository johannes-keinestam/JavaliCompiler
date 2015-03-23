package cd;

import java.io.File;

public class Config {

	public static enum SystemKind {
		LINUX, WINDOWS, MACOSX
	};

	/**
	 * If using X86-64, this flag should be set to true
	 */
	public static final boolean x64_tests = false;

	/**
	 * What kind of system we are on
	 */
	public static final SystemKind systemKind;

	/**
	 * Defines the extension used for assembler files on this platform.
	 * Currently always {@code .s}.
	 */
	public static final String ASMEXT = ".s";

	/** Defines the extension used for binary files on this platform. */
	public static final String BINARYEXT;

	/** Defines the name of the main function to be used in .s file */
	public static final String MAIN;

	/** Defines the name of the printf function to be used in .s file */
	public static final String PRINTF;

	/** Defines the name of the scanf function to be used in .s file */
	public static final String SCANF;

	/** Defines the name of the malloc function to be used in .s file */
	public static final String MALLOC;

	/** Defines the name of the putchar function to be used in .s file */
	public static final String PUTCHAR;
	
	/** Defines the name of the exit function to be used in .s file */
	public static final String EXIT;

	/** The assembler directive used to define a constant string */
	public static final String DOT_STRING;

	/** The assembler directive used to define a constant int */
	public static final String DOT_INT;

	/** The assembler directive used to start the text section */
	public static final String TEXT_SECTION;

	/** The assembler directive used to start the section for integer data */
	public static final String DATA_INT_SECTION;

	/**
	 * The assembler directive used to start the section for string data (if
	 * different from {@link #DATA_INT_SECTION}
	 */
	public static final String DATA_STR_SECTION;

	/** Comment separator used in assembly files */
	public static final String COMMENT_SEP;

	/**
	 * Defines the assembler command to use. Should be a string array where each
	 * entry is one argument. Use the special string "$0" to refer to the output
	 * file, and $1 to refer to the ".s" file.
	 */
	public static final String[] ASM;

	/**
	 * The directory from which to run the assembler. In a CYGWIN installation,
	 * this can make a big difference!
	 */
	public static final File ASM_DIR;

	/**
	 * sizeof a pointer in bytes in the target platform.
	 */
	public static final int SIZEOF_PTR = 4;
	
	/**
	 * Perform constant folding.
	 */
	public static final boolean CFOLD = true;
	/**
	 * Perform dead assignment statement elimination.
	 */
	public static final boolean DSTMT = false;

	static {

		String os = System.getProperty("os.name").toLowerCase();

		if (os.indexOf("windows") != -1 || os.indexOf("nt") != -1) {
			systemKind = SystemKind.WINDOWS;
			BINARYEXT = ".exe";
			MAIN = "_main";
			PRINTF = "_printf";
			SCANF = "_scanf";
			MALLOC = "_malloc";
			PUTCHAR = "_putchar";
			EXIT = "_exit";
			// These are set up for a Cygwin installation on C:,
			// you can change as needed.
			ASM = new String[] { "gcc", "-o", "$0", "$1" };
			ASM_DIR = new File("C:\\CYGWIN\\BIN");
			DOT_STRING = ".string";
			DOT_INT = ".int";
			TEXT_SECTION = ".section .text";
			DATA_INT_SECTION = ".section .data";
			DATA_STR_SECTION = ".section .data";
			COMMENT_SEP = ";";
		} else if (os.equals("mac os x") || os.equals("darwin")) {
			systemKind = SystemKind.MACOSX;
			BINARYEXT = ".bin";
			MAIN = "_main";
			PRINTF = "_printf";
			SCANF = "_scanf";
			MALLOC = "_malloc";
			PUTCHAR = "_putchar";
			EXIT = "_exit";
			ASM = new String[] { "gcc", "-m32", "-o", "$0", "$1" };
			ASM_DIR = new File(".");
			DOT_STRING = ".asciz";
			DOT_INT = ".long";
			TEXT_SECTION = ".text";
			DATA_INT_SECTION = ".data";
			DATA_STR_SECTION = ".cstring";
			COMMENT_SEP = "#";
		} else {
			systemKind = SystemKind.LINUX;
			BINARYEXT = ".bin";
			MAIN = "main";
			PRINTF = "printf";
			SCANF = "scanf";
			MALLOC = "malloc";
			PUTCHAR = "putchar";
			EXIT = "exit";
			ASM = new String[] { "gcc", "-m32", "-o", "$0", "$1" };
			ASM_DIR = new File(".");
			DOT_STRING = ".string";
			DOT_INT = ".int";
			TEXT_SECTION = ".section .text";
			DATA_INT_SECTION = ".section .data";
			DATA_STR_SECTION = ".section .data";
			COMMENT_SEP = "#";
		}
	}

}
