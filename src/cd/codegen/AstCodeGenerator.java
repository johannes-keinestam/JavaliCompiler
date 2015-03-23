package cd.codegen;

import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cd.Main;
import cd.ir.Ast.ClassDecl;

/**
 * Main class for generating code. Mainly works as a hub containing
 * all other visitors and class/method/field information.
 */
public class AstCodeGenerator {

	// All different visitors for emitting code. Refer to the classes
	// for comments.
	protected final ExprGenerator eg = new ExprGenerator(this);
	protected final StmtDeclGenerator sdg = new StmtDeclGenerator(this);
	protected final AddressGenerator av = new AddressGenerator(this);
	protected final InitializationGenerator iv = new InitializationGenerator(this);
	protected final Main main;
	protected final RegisterPool registerPool = new RegisterPool(this);

	// Data about the classes that exist in the program and the offsets of their field (in respect to the instance
	// pointer), their methods (in respect to the vtable pointer) and its methods locals (in respect to EBP).
	private final Map<String, ClassOffsets> classes = new HashMap<String, ClassOffsets>();
	// Pointer to the current class being processed. Used for finding the corresponding field/method
	// offset needed in its method declarations.
	protected ClassOffsets currentClass;
	// Pointer to the current method being processed. Used for finding the corresponding local variable
	// offsets needed in expressions.
	protected String currentMethod;

	public AstCodeGenerator(Main main, Writer out) {
		this.main = main;
		AssemblerHelper.init(this, out);
	}

	public void debug(String format, Object... args) {
		this.main.debug(format, args);
	}

	/**
	 * Main method. Causes us to emit x86 assembly corresponding to {@code ast}
	 * into {@code file}. Throws a {@link RuntimeException} should any I/O error
	 * occur.
	 */
	public void go(List<? extends ClassDecl> astRoots) {
		// Emit vtables
		iv.go(astRoots);
		// Emit standard library
		StdLibEmitter.emitAll(this);
		// Emit program code
		for (ClassDecl ast : astRoots) {
			currentClass = getClassOffsets(ast.name);
			sdg.gen(ast);
		}
	}

	public ClassOffsets getClassOffsets(String name) {
		if (!classes.containsKey(name)) {
			classes.put(name, new ClassOffsets(name));
		}
		return classes.get(name);
	}

	/**
	 * Class containing information about the offsets of the declared classes in the program.
	 */
	public class ClassOffsets {
		private final Map<String, Integer> fieldOffsets = new HashMap<String, Integer>();
		private final Map<String, Integer> methodOffsets = new HashMap<String, Integer>();
		private final HashMap<String, Map<String, Integer>> methodVariableOffsets = new HashMap<String, Map<String, Integer>>();
		public final String name;

		public ClassOffsets(String name) {
			this.name = name;
		}

		public void addField(String name, int offset) {
			fieldOffsets.put(name, offset);
		}

		public int getField(String name) {
			return fieldOffsets.get(name);
		}

		public Collection<String> getFields() {
			return fieldOffsets.keySet();
		}

		public void addMethod(String name, int offset) {
			methodOffsets.put(name, offset);
		}

		public int getMethod(String name) {
			return methodOffsets.get(name);
		}

		public void addLocal(String methodName, String localName, int offset) {
			if (!methodVariableOffsets.containsKey(methodName)) {
				methodVariableOffsets.put(methodName, new HashMap<String, Integer>());
			}
			methodVariableOffsets.get(methodName).put(localName, offset);
		}

		public int getLocal(String methodName, String localName) {
			return methodVariableOffsets.get(methodName).get(localName);
		}

		public boolean containsLocal(String methodName, String localName) {
			return methodVariableOffsets.get(methodName).containsKey(localName);
		}


	}


}
