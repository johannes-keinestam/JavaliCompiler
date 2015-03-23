package cd.codegen;

import static cd.codegen.AssemblerHelper.*;
import cd.Config;
import cd.ir.Ast.NewObject;

/*
 * Class that emits our Javali "standard library", including error
 * handling, the main method and some helper functions.
 */
public class StdLibEmitter {
	
	private static AstCodeGenerator acg;
	protected static String CAST_EXCEPTION = "CastException";
	protected static String DIVISION_BY_ZERO_EXCEPTION = "DivisionByZeroException";
	protected static String ILLEGAL_ARRAY_SIZE_EXCEPTION = "IllegalArraySizeException";
	protected static String INDEX_OUT_OF_BOUNDS_EXCEPTION = "IndexOutOfBoundsException";
	protected static String NULL_POINTER_EXCEPTION = "NullPointerException";


	public static void emitAll(AstCodeGenerator acg) {
		StdLibEmitter.acg = acg;
		constants();
		mainMethod();
		castValidate();
		exceptions();
	}
	
	private static void constants() {
		emit("");
		emit(".section .rodata");
		emitDeclaration("int_format_string", "string", "\"%d\"");
		emitDeclaration("float_format_string", "string", "\"%f\"");
		emitDeclaration("divide_by_zero_exception_string", "string", "\"EXCEPTION: Division by zero.\\n\"");
		emitDeclaration("cast_exception_string", "string", "\"EXCEPTION: Invalid cast.\\n\"");
		emitDeclaration("illegal_array_size_exception_string", "string", "\"EXCEPTION: Illegal Array Size.\\n\"");
		emitDeclaration("index_out_of_bounds_exception_string", "string", "\"EXCEPTION: Array index out of bounds.\\n\"");
		emitDeclaration("null_pointer_exception_string", "string", "\"EXCEPTION: Null pointer.\\n\"");
	}
	
	/*
	 * Emits the function for dynamically validating casting.
	 */
	private static void castValidate() {
        // Check downcast
		String verifyCastLoopLabel = uniqueLabel();
		String castSuccessLabel = uniqueLabel();

		String fromVtableReg = acg.registerPool.reserve();
		String objectVtableReg = acg.registerPool.reserve();
		String toVtableReg = acg.registerPool.reserve();

		emitLabel("CastValidate");
		emitIndent("");
		emitMethodPrefix();
		
		emitLoad(8, "%ebp", toVtableReg); // vtable of To Class is first argument to function
		emitLoad(12, "%ebp", fromVtableReg); // vtable of From Class is second argument to function

		emitMove(c("vtable_Object"), objectVtableReg);

		emitLabel(verifyCastLoopLabel);
		emit("cmpl", fromVtableReg, objectVtableReg); 
		emit("je", CAST_EXCEPTION); // If current class is object, fail (reached end of hierarchy)
		
		emit("cmpl", fromVtableReg, toVtableReg);
		emit("je", castSuccessLabel); // if current class is equal to the cast to-type, succeed
		emitLoad(0, fromVtableReg, fromVtableReg); // move from pointer to its super class
		
		emit("jmp", verifyCastLoopLabel);
		
		emitLabel(castSuccessLabel);
		acg.registerPool.release(toVtableReg);
		acg.registerPool.release(fromVtableReg);
		acg.registerPool.release(objectVtableReg);

		emitMethodSuffix(false);
		emitUndent();
	}
	
	private static void exceptions() {
		emitException(CAST_EXCEPTION, "cast_exception_string", 1);
		emitException(DIVISION_BY_ZERO_EXCEPTION, "divide_by_zero_exception_string", 8);
		emitException(ILLEGAL_ARRAY_SIZE_EXCEPTION, "illegal_array_size_exception_string", 5);
		emitException(INDEX_OUT_OF_BOUNDS_EXCEPTION, "index_out_of_bounds_exception_string", 3);
		emitException(NULL_POINTER_EXCEPTION, "null_pointer_exception_string", 4);
	}
	
	private static void emitException(String name, String errorMsg, int code) {
		emitLabel(name);
		emitIndent(null);
		emit("pushl", c(errorMsg));
		emit("call", Config.PRINTF);
		emit("pushl", c(code));
		emit("call", Config.EXIT);
		emitUndent();
	}
	
	/*
	 * Emits the main method, which just creates a Main object m
	 * and calls m.main();
	 */
	private static void mainMethod(){
		emit(".text");
		emit(".globl", Config.MAIN);
		emitLabel(Config.MAIN);
		emitIndent(null);

		emitMethodPrefix();

		// Reuse newObject expression to emit creation of first Main object
		String mainObjReg = acg.eg.newObject(new NewObject("Main"), null);
		
		// Call m.main();
		emit("pushl", mainObjReg);
		acg.registerPool.release(mainObjReg);
		emit("call", "Main_main");
		
		emitDeallocation(4);
		emitMethodSuffix(true);

		emitUndent();
	}
}
