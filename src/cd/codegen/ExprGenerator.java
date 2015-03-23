package cd.codegen;

import java.util.Collection;
import java.util.List;

import cd.Config;
import cd.debug.AstOneLine;
import cd.ir.Ast;
import cd.ir.ExprVisitor;
import cd.ir.Symbol;
import cd.ir.Ast.BinaryOp;
import cd.ir.Ast.BooleanConst;
import cd.ir.Ast.BuiltInRead;
import cd.ir.Ast.BuiltInReadFloat;
import cd.ir.Ast.Cast;
import cd.ir.Ast.Expr;
import cd.ir.Ast.Field;
import cd.ir.Ast.FloatConst;
import cd.ir.Ast.Index;
import cd.ir.Ast.IntConst;
import cd.ir.Ast.MethodCallExpr;
import cd.ir.Ast.NewArray;
import cd.ir.Ast.NewObject;
import cd.ir.Ast.NullConst;
import cd.ir.Ast.ThisRef;
import cd.ir.Ast.UnaryOp;
import cd.ir.Ast.Var;
import cd.ir.Symbol.ClassSymbol;
import cd.ir.Symbol.TypeSymbol;
import static cd.codegen.AssemblerHelper.*;

/**
 * Generates code to evaluate expressions. After emitting the code, returns
 * a String which indicates the register where the result can be found.
 */
class ExprGenerator extends ExprVisitor<String, Void> {

	/**
	 * 
	 */
	private final AstCodeGenerator acg;

	/**
	 * @param astCodeGenerator
	 */
	ExprGenerator(AstCodeGenerator astCodeGenerator) {
		acg = astCodeGenerator;
	}

	public String gen(Expr ast) {
		return visit(ast, null);
	}

	@Override
	public String visit(Expr ast, Void arg) {

		try {
			emitIndent("Emitting " + AstOneLine.toString(ast));
			//System.out.println("\t"+AstOneLine.toString(ast) + ". Regs: "+registerPool.availableRegisters.size());
			return super.visit(ast, null);
		} finally {
			emitUndent();
		}

	}

	@Override
	public String binaryOp(BinaryOp ast, Void arg) {
		// Always evaluate in left to right order
		String regLeft = visit(ast.left(), arg);
		boolean isFloatOperation = ast.left().type.name.equals("float");

		String shortCircuitLabel = emitShortCircuiting(ast, regLeft);

		// Always push registers when not needed, register spilling
		emit("pushl", regLeft);
		acg.registerPool.release(regLeft);
		String regRight = visit(ast.right(), arg);
		emit("pushl", regRight);
		acg.registerPool.release(regRight);
		String rhs = o(0, "%esp");
		regLeft = acg.registerPool.reserve(regLeft); // Re-reserve regLeft
		emitLoad(4, "%esp", regLeft);
		
		// Register of the least significant byte of
		// regLeft. Needed for zero-extending in boolean
		// operations.
		String byteReg = "%" + regLeft.charAt(2) + "l";
		boolean performedFloatOp = false;
		
		if (isFloatOperation){
			// If this is a float operation, load the floats into the FPU first
			emit("pushl", regLeft);
			String lhs = rhs;
			rhs = o(4, "%esp");
			emit("fld", rhs);
			emit("fld", lhs);
		}
		
		switch (ast.operator) {
		case B_PLUS:
			if (isFloatOperation) {
				emit("faddp");
				performedFloatOp = true;
			} else
				emit("addl", rhs, regLeft);
			break;
		case B_MINUS:
			if (isFloatOperation) {
				emit("fsubp");
				performedFloatOp = true;
			} else
				emit("subl", rhs, regLeft);
			break;
		case B_TIMES:
			if (isFloatOperation) {
				emit("fmulp");
				performedFloatOp = true;
			} else
				emit("imull", rhs, regLeft);
			break;
		case B_DIV:
			if (isFloatOperation) {
				emit("fdivp");
				performedFloatOp = true;
			} else {
				emit("cmpl", c(0), rhs);
				emit("je", StdLibEmitter.DIVISION_BY_ZERO_EXCEPTION);
				emit("pushl", "%eax");
				emitMove(regLeft, "%eax");
				emit("cltd");
				emit("idivl", rhs);
				emitMove("%eax", regLeft);
				emit("popl", "%eax");
			}
			break;
		case B_MOD:
			emit("pushl", "%eax");
			emitMove(regLeft, "%eax");
			emit("cltd");
			emit("idivl", rhs);
			emitMove("%edx", regLeft);
			emit("popl", "%eax");
			break;
		case B_AND:
			// Perform AND
			emit("andl", rhs, regLeft);
			break;
		case B_OR:
			// Perform OR
			emit("orl", rhs, regLeft);
			break;
		case B_EQUAL:
			emit("cmpl", rhs, regLeft);
			emit("sete", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		case B_NOT_EQUAL:
			emit("cmpl", rhs, regLeft);
			emit("setne", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		case B_GREATER_OR_EQUAL:
			emit("cmpl", rhs, regLeft);
			emit("setge", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		case B_GREATER_THAN:
			emit("cmpl", rhs, regLeft);
			emit("setg", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		case B_LESS_OR_EQUAL:
			emit("cmpl", rhs, regLeft);
			emit("setle", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		case B_LESS_THAN:
			emit("cmpl", rhs, regLeft);
			emit("setl", byteReg);
			emit("movzx", byteReg, regLeft);
			break;
		default:
			break;
		}
		if (performedFloatOp) {
			// If float operation has been performed (i.e. for ADD, SUB, MUL or DIV),
			// Load the value out of the FPU before returning.
			emit("fstp", rhs);
			emitMove(rhs, regLeft);
			// FPU loading required an extra pushed value.
			emitDeallocation(12);
		} else {
			emitDeallocation(8);
		}
		// Jump here if the operation could short circuit.
		emitLabel(shortCircuitLabel);
		return regLeft;
	}

	/**
	 * Emits code for short circuiting.
	 * @param ast The BinaryOp Ast.
	 * @param register The register to check the value of.
	 * @return The label short circuiting will jump to.
	 */
	private String emitShortCircuiting(BinaryOp ast, String register) {
		// Short circuiting.
		String shortCircuitLabel = uniqueLabel();
		switch(ast.operator) {
			case B_AND:
				//If lhs is false, jump out of calculation
				//immediately and return false.
				emit("cmpl", c(0), register);
				emit("je", shortCircuitLabel);
				break;
			case B_OR:
				//If lhs is true, jump out of calculation
				//immediately and return false.
				emit("cmpl", c(0), register);
				emit("jne", shortCircuitLabel);
				break;
			default:
				break;
		}
		return shortCircuitLabel;
	}

	@Override
	public String booleanConst(BooleanConst ast, Void arg) {
		String reg = acg.registerPool.reserve();
		// We represent booleans as 1 (true) or 0 (false).
		int val = ast.value ? 1 : 0;
		emitMove(c(val), reg);
		return reg;
	}

	@Override
	public String builtInRead(BuiltInRead ast, Void arg) {
		return emitBuiltInReadCall("int_format_string");
	}

	@Override
	public String builtInReadFloat(BuiltInReadFloat ast, Void arg) {
		return emitBuiltInReadCall("float_format_string");
	}

	private String emitBuiltInReadCall(String formatStringName) {
		// allocate memory for return value
		emitAllocation(4);
		// call function
		emit("pushl", "%esp");
		emit("pushl", c(formatStringName));
		emit("call", Config.SCANF);
		emitDeallocation(8);

		String addressReg = acg.registerPool.reserve();
		emitLoad(0, "%esp", addressReg); // Convert pointer to value
		// move result from memory to register
		return addressReg;
	}

	@Override
	public String cast(Cast ast, Void arg) {
		String castToType = ast.typeName;
		// vtables for array types are called vtables_elemtype_Array
		if (castToType.contains("[")) {
			castToType = castToType.split("\\[")[0].trim().concat("_Array");
		}
		String fromInstanceAddrReg = visit(ast.arg(), arg);

		// Cast to object always succeeds
        if (castToType.equals("Object"))
            return fromInstanceAddrReg;

		// Upcast between classes should always succeed.
        if (ast.arg().type instanceof Symbol.ClassSymbol) {
        	Symbol.ClassSymbol c = (ClassSymbol)ast.arg().type;
	        while (c.name != "Object") {
	            if (c.name.equals(castToType))
	                return fromInstanceAddrReg;
	            c = c.superClass;
	        }
        }
        
        // Call helper to verify correct downcast.
        emit("pushl", o(0, fromInstanceAddrReg)); // Push address to vTable of From Class as argument
		emit("pushl", String.format("$vtable_%s", castToType)); // Push address of vTable of To Class as argument
		emit("call", "CastValidate");
		emitDeallocation(8);
        
		return fromInstanceAddrReg;
	}

	@Override
	public String index(Index ast, Void arg) {
		String indexAddrReg = acg.av.visit(ast, arg);
		emitLoad(0, indexAddrReg, indexAddrReg);
		return indexAddrReg;
	}

	@Override
	public String intConst(IntConst ast, Void arg) {
		String reg = acg.registerPool.reserve();
		emitComment("Int constant " + ast.value);
		emitMove(c(ast.value), reg);
		return reg;
	}

	@Override
	public String floatConst(FloatConst ast, Void arg) {
		String reg = acg.registerPool.reserve();
		int floatAsInt = Float.floatToRawIntBits(ast.value);
		emitComment("Float constant " + ast.value);
		emitMove(c(floatAsInt), reg); // Write all floats as ints
		return reg;
	}

	@Override
	public String field(Field ast, Void arg) {
		// For the sake of nicer Java code, getting the value can be seen as a special
		// case of getting the address of the field, and then loading it.
		String fieldAddrReg = acg.av.field(ast, arg);
		emitLoad(0, fieldAddrReg, fieldAddrReg);
		return fieldAddrReg;
	}

	@Override
	public String newArray(NewArray ast, Void arg) {
		String lengthReg = visit(ast.arg(), arg);
		String byteLengthReg = acg.registerPool.reserve();
		emitComment("Calculate byte length of array from element length");

		// Jump if negative array size. Size 0 is supported.
		emit("cmpl", c(0), lengthReg);
		emit("jl", StdLibEmitter.ILLEGAL_ARRAY_SIZE_EXCEPTION);

		// Calculate the required length it bytes and allocate it on the heap it.
		emitMove(lengthReg, byteLengthReg);
		emit("imull", c(4), byteLengthReg);
		// Add space for vtable and capacity
		emit("addl", c(8), byteLengthReg);
		String arrReg = allocateMemory(byteLengthReg);

		// Arrays has a pointer to its vtable as its first element
		String elementTypeName = ast.typeName.split("\\[")[0].trim();
		emitMove(c("vtable_" + elementTypeName + "_Array"), o(0, arrReg));
		// Arrays has a capacity field as its second element.
		emitStore(lengthReg, 4, arrReg);

		acg.registerPool.release(byteLengthReg);
		acg.registerPool.release(lengthReg);
		return arrReg;
	}

	/*
	 * Allocates memory using malloc and then returns the register
	 * containing the memory address.
	 */
	private String allocateMemory(String byteSizeRegister) {
		boolean pushEax = pushEax();
		emit("pushl", byteSizeRegister);
		emit("call", Config.MALLOC);
		emitDeallocation(4); // Remove argument
		String addrReg = acg.registerPool.reserve();
		emitMove("%eax", addrReg);
		if (pushEax)
			restoreEax();
		return addrReg;
	}

	@Override
	public String newObject(NewObject ast, Void arg) {
		// Allocate memory for the new object, with size for the vtable pointer and
		// all fields.
		Collection<String> fieldInstanceTable = acg.getClassOffsets(ast.typeName).getFields();
		emitComment("Creating object of type " + ast.typeName);
		String objectReg = allocateMemory(c(4 + fieldInstanceTable.size() * 4));

		// Set vtable pointer as its first element.
		emitStore(c("vtable_" + ast.typeName), 0, objectReg);
		return objectReg;
	}

	@Override
	public String nullConst(NullConst ast, Void arg) {
		String nullReg = acg.registerPool.reserve();
		// An object set to null is represented by its address set to 0x00000000.
		emitMove(c(0), nullReg);
		return nullReg;
	}

	@Override
	public String thisRef(ThisRef ast, Void arg) {
		// A reference to this is always set as the first argument in the method.
		int thisOffset = acg.currentClass.getLocal(acg.currentMethod, "this");
		String receiverReg = acg.registerPool.reserve();
		emitLoad(thisOffset, "%ebp", receiverReg);
		return receiverReg;
	}

	@Override
	public String methodCall(MethodCallExpr ast, Void dummy) {
		boolean pushEax = pushEax();
		String methodAddressRegister = getMethodPointer(ast);
		List<Expr> args = ast.allArguments();
		
		for (int i = args.size() - 1; i >= 0; i--) {
			Expr arg = args.get(i);
			String reg = acg.eg.visit(arg, dummy);
			emit("pushl", reg);
			acg.registerPool.release(reg);
		}
		emit("call", "*" + methodAddressRegister);

		emitDeallocation(args.size() * 4);
		emitMove("%eax", methodAddressRegister);

		if(pushEax) restoreEax();
		return methodAddressRegister;
	}

	private String getMethodPointer(MethodCallExpr ast) {
		// Loads the pointer of the method from the receivers vtable and returns it.
		Ast.Expr rcvr = ast.receiver();
		TypeSymbol type = rcvr.type; //Resolve static type of receiver
		String className = type.name;
		String receiverReg = visit(rcvr, null);
		// Check that you are not trying to call a method on a null pointer
		emit("cmpl", c(0), receiverReg);
		emit("je", StdLibEmitter.NULL_POINTER_EXCEPTION);

		emitLoad(0, receiverReg, receiverReg); // vtable now in receiver reg

		int methodOffset = acg.getClassOffsets(className).getMethod(ast.methodName); //Get offset for methodcall

		emitLoad(methodOffset, receiverReg,receiverReg);
		return receiverReg;
	}

	@Override
	public String unaryOp(UnaryOp ast, Void arg) {
		String argReg = visit(ast.arg(), arg);
		switch (ast.operator) {
		case U_PLUS:
			break;
		case U_MINUS:
			emit("negl", argReg);
			break;
		case U_BOOL_NOT:
			emit("negl", argReg);
			emit("incl", argReg);
			break;
		}
		return argReg;
	}

	@Override
	public String var(Var ast, Void arg) {
		// For the sake of nicer Java code, getting the value can be seen as a special
		// case of getting the address of the var, and then loading it.
		String varAddrReg = acg.av.var(ast, arg);
		emitLoad(0, varAddrReg, varAddrReg);
		return varAddrReg;
	}

}