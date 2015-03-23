package cd.codegen;

import cd.ir.ExprVisitor;
import cd.ir.Ast.Field;
import cd.ir.Ast.Index;
import cd.ir.Ast.ThisRef;
import cd.ir.Ast.Var;
import static cd.codegen.AssemblerHelper.*;

/**
 * Generator for producing addresses of variables and other.
 * Used on the right side of an assignment, when not the value
 * but the register at which the variable/field/array index is
 * stored is required in a register.
 */
class AddressGenerator extends ExprVisitor<String, Void> {
	private final AstCodeGenerator acg;

	/**
	 * @param astCodeGenerator
	 */
	AddressGenerator(AstCodeGenerator astCodeGenerator) {
		this.acg = astCodeGenerator;
	}

	/**
	 * Returns the address of a variable in a register.
	 */
	@Override
	public String var(Var ast, Void arg) {
		// Sometimes a field is accessed without the this keyword. This is allowed, but will be
		// interpreted as an access to a var instead.
		if (!acg.currentClass.containsLocal(acg.currentMethod, ast.name)) {
			// Allow access to fields without this keyword
			Field f = new Field(new ThisRef(), ast.name);
			return visit(f, arg);
		}
		int offset = acg.currentClass.getLocal(acg.currentMethod, ast.name);
		String reg = acg.registerPool.reserve();
		emit("leal", o(offset, "%ebp"), reg);
		return reg;
	}

	/**
	 * Returns the address of an array index in a register.
	 */
	@Override
	public String index(Index ast, Void arg) {
		String arrayReg = visit(ast.left(), arg);
		emitLoad(0, arrayReg, arrayReg);
		// Check that you are not trying to index on a null pointer
		emit("cmpl", c(0), arrayReg);
		emit("je", StdLibEmitter.NULL_POINTER_EXCEPTION);
		
		String indexReg = acg.eg.visit(ast.right(), arg);
		// Check that array index is not out of bounds
		emit("cmpl", c(0), indexReg);
		emit("jl", StdLibEmitter.INDEX_OUT_OF_BOUNDS_EXCEPTION); //jump if index is less then zero
		emit("cmpl", o(4, arrayReg), indexReg);
		emit("jge", StdLibEmitter.INDEX_OUT_OF_BOUNDS_EXCEPTION); //jump if index greater or equal to the arraysize, o(4,arrayReg)
		
		emit("imull", c(4), indexReg);
		
		emit("addl", c(8), arrayReg); // Offset vtable and capacity of array
		emit("addl", indexReg, arrayReg);
		acg.registerPool.release(indexReg);
		return arrayReg;
	}

	/**
	 * Returns the address of a field in a register.
	 */
	@Override
	public String field(Field ast, Void arg) {
		String objPointerReg = acg.eg.visit(ast.arg(), arg);
		// Check that you are not trying to get the field of a null pointer
		emit("cmpl", c(0), objPointerReg);
		emit("je", StdLibEmitter.NULL_POINTER_EXCEPTION);
		// Proceed to get field if non-null
		String staticClassName = ast.arg() instanceof ThisRef ? acg.currentClass.name : ast.sym.getStaticClass().name;
		int offset = acg.getClassOffsets(staticClassName).getField(ast.fieldName);
		emit("addl", c(offset), objPointerReg);
		return objPointerReg;
	}
}