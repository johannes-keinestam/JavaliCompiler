package cd.codegen;

import java.io.IOException;
import java.io.Writer;

import cd.Config;

/**
 * A helper with static methods for emitting code.
 */
public class AssemblerHelper {
	private static StringBuilder indent = new StringBuilder();
	private static int counter = 0;
	private static Writer out;
	private static AstCodeGenerator acg;

	protected static void init(AstCodeGenerator acg, Writer writer) {
		out = writer;
		AssemblerHelper.acg = acg;
	}
	
	protected static boolean pushEax() {
		boolean pushEax = acg.registerPool.isInUse("%eax");
		if (pushEax)
			emit("pushl", "%eax");
		return pushEax;
	}
	
	protected static void restoreEax(){
		emit("popl", "%eax");
	}
	
	/** Creates an constant operand relative to another operand. */
	protected static String c(int i) {
		return "$" + i;
	}

	/** Creates an constant operand with the address of a label. */
	protected static String c(String lbl) {
		return "$" + lbl;
	}

	/** Creates an operand relative to another operand. */
	protected static String o(int offset, String reg) {
		return String.format("%d(%s)", offset, reg);
	}

	/** Creates an operand addressing an item in an array */
	protected static String a(String arrReg, String idxReg) {
		final int offset = Config.SIZEOF_PTR; // one word in front for vptr
		final int mul = Config.SIZEOF_PTR; // assume all arrays of 4-byte elem
		return String.format("%d(%s,%s,%d)", offset, arrReg, idxReg, mul);
	}

	protected static void emitIndent(String comment) {
		indent.append("  ");
		if (comment != null)
			emitComment(comment);
	}

	protected static void emitCommentSection(String name) {
		int indentLen = indent.length();
		int breakLen = 68 - indentLen - name.length();
		StringBuffer sb = new StringBuffer();
		sb.append(Config.COMMENT_SEP).append(" ");
		for (int i = 0; i < indentLen; i++)
			sb.append("_");
		sb.append(name);
		for (int i = 0; i < breakLen; i++)
			sb.append("_");

		try {
			out.write(sb.toString());
			out.write("\n");
		} catch (IOException e) {
		}
	}

	protected static void emitComment(String comment) {
		emit(Config.COMMENT_SEP + " " + comment);
	}

	protected static void emitUndent() {
		indent.setLength(indent.length() - 2);
	}

	protected static void emit(String op, String src, String dest) {
		emit(String.format("%s %s, %s", op, src, dest));
	}

	public static void emit(String op, int src, String dest) {
		emit(op, c(src), dest);
	}

	protected static void emit(String op, String dest) {
		emit(op + " " + dest);
	}

	protected static void emit(String op, int dest) {
		emit(op, c(dest));
	}

	protected static void emitMove(String src, String dest) {
		if (!src.equals(dest))
			emit("movl", src, dest);
	}

	protected static void emitLoad(int srcOffset, String src, String dest) {
		emitMove(o(srcOffset, src), dest);
	}

	protected static void emitStore(String src, int destOffset, String dest) {
		emitMove(src, o(destOffset, dest));
	}

	protected static void emitConstantData(String data) {
		emit(String.format("\t%s %s", Config.DOT_INT, data));
	}

	protected static void emitDeclaration(String name, String type, String value) {
		emit(String.format("%s:\n\t.%s %s", name, type, value));
	}


	protected static String uniqueLabel() {
		String labelName = "label" + counter++;
		return labelName;
	}

	protected static void emitLabel(String main) {
		try {
			out.write(main + ":" + "\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected static void emit(String op) {
		try {
			out.write(indent.toString());
			out.write(op);
			out.write("\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected static void emitMethodSuffix(boolean returnNull) {
		if (returnNull)
			emit("movl", "$0", "%eax");
		emit("leave");
		emit("ret");
	}

	protected static void emitMethodPrefix() {
		emit("pushl", "%ebp");
		emitMove("%esp", "%ebp");
	}

	protected static void emitAllocation(int bytes) {
		emit("subl", c(bytes), "%esp");
	}

	protected static void emitDeallocation(int bytes) {
		emit("addl", c(bytes), "%esp");
	}

}
