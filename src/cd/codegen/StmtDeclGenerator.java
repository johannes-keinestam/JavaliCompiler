package cd.codegen;

import static cd.codegen.AssemblerHelper.c;
import static cd.codegen.AssemblerHelper.emit;
import static cd.codegen.AssemblerHelper.emitAllocation;
import static cd.codegen.AssemblerHelper.emitComment;
import static cd.codegen.AssemblerHelper.emitDeallocation;
import static cd.codegen.AssemblerHelper.emitIndent;
import static cd.codegen.AssemblerHelper.emitLabel;
import static cd.codegen.AssemblerHelper.emitMethodPrefix;
import static cd.codegen.AssemblerHelper.emitMethodSuffix;
import static cd.codegen.AssemblerHelper.emitMove;
import static cd.codegen.AssemblerHelper.emitStore;
import static cd.codegen.AssemblerHelper.emitUndent;
import static cd.codegen.AssemblerHelper.o;
import static cd.codegen.AssemblerHelper.uniqueLabel;

import java.util.List;

import cd.Config;
import cd.debug.AstOneLine;
import cd.ir.Ast;
import cd.ir.Ast.Assign;
import cd.ir.Ast.BuiltInWrite;
import cd.ir.Ast.BuiltInWriteFloat;
import cd.ir.Ast.BuiltInWriteln;
import cd.ir.Ast.IfElse;
import cd.ir.Ast.MethodCall;
import cd.ir.Ast.MethodDecl;
import cd.ir.Ast.ReturnStmt;
import cd.ir.Ast.VarDecl;
import cd.ir.Ast.WhileLoop;
import cd.ir.AstVisitor;

/**
 * Generates code to process statements and declarations.
 */
public class StmtDeclGenerator extends AstVisitor<String, Void> {

	private final AstCodeGenerator acg;

	StmtDeclGenerator(AstCodeGenerator astCodeGenerator) {
		this.acg = astCodeGenerator;
	}

	public void gen(Ast ast) {
		visit(ast, null);
	}

	@Override
	public String visit(Ast ast, Void arg) {
		try {
			emitIndent("Emitting " + AstOneLine.toString(ast));
			return super.visit(ast, arg);
		} finally {
			emitUndent();
		}
	}

	@Override
	public String methodCall(MethodCall ast, Void dummy) {
		// A MethodCall is just a MethodCallExpr where we do not care about
		// the return value.
		Ast.MethodCallExpr expr = new Ast.MethodCallExpr(ast.receiver(), ast.methodName,
														 ast.argumentsWithoutReceiver());
		String returnValReg = acg.eg.methodCall(expr, dummy);
		acg.registerPool.release(returnValReg);
		return null;
	}

	@Override
	public String methodDecl(MethodDecl ast, Void arg) {
		acg.currentMethod = ast.name;
		emitLabel(String.format("%s_%s", acg.currentClass.name, ast.name));
		emitIndent(null);
		emitMethodPrefix();
		List<String> arguments = ast.argumentNames;
		arguments.add(0, "this"); // all methods receive this reference
		generateDeclarations(ast.argumentNames, ast.decls().rwChildren());

		acg.sdg.visit(ast.body(), arg);

		// Method does not explicitly return -- return manually
		int lastItemIndex = ast.body().children().size() - 1;
		Ast lastStatement = lastItemIndex != -1 ? ast.body().children().get(lastItemIndex) : null;
		if (!(lastStatement instanceof ReturnStmt)) {
			emitMethodSuffix(true);
		}
		emitUndent();
		return null;
	}

	/**
	 * Generates declarations of the locals (arguments and method variables).
	 * @param arguments Names of arguments
	 * @param declarations Declared variables.
	 */
	private void generateDeclarations(List<String> arguments,
			List<Ast> declarations) {
		int offset = 8;
		for (String argument : arguments) {
			acg.currentClass.addLocal(acg.currentMethod, argument, offset);
			offset += 4;
		}

		if (!declarations.isEmpty()) {
			emitAllocation(declarations.size() * 4);
		}
		offset = -4;
		for (Ast declaration : declarations) {
			VarDecl decVar = (VarDecl) declaration;
			acg.currentClass.addLocal(acg.currentMethod, decVar.name, offset);
			offset -= 4;
		}

	}

	@Override
	public String ifElse(IfElse ast, Void arg) {
		String elseLabel = uniqueLabel();
		String endLabel = uniqueLabel();

		String condReg = acg.eg.visit(ast.condition(), arg);
		emit("cmpl", c(0), condReg);
		acg.registerPool.release(condReg);
		emit("je", elseLabel); // jump to else if condition is false
		acg.sdg.visit(ast.then(), arg);
		emit("jmp", endLabel);
		emitLabel(elseLabel);
		acg.sdg.visit(ast.otherwise(), arg);
		emitLabel(endLabel);
		return null;
	}

	@Override
	public String whileLoop(WhileLoop ast, Void arg) {
		String startOfWhile = uniqueLabel();
		String endOfWhile = uniqueLabel();
		emitLabel(startOfWhile);
		String condReg = acg.eg.visit(ast.condition(), arg);
		emit("cmpl", c(0), condReg);
		acg.registerPool.release(condReg);
		emit("je", endOfWhile); // jump out of loop if condition no longer
								// holds
		acg.sdg.visit(ast.body(), arg);
		emit("jmp", startOfWhile);
		emitLabel(endOfWhile);
		return null;
	}

	@Override
	public String assign(Assign ast, Void arg) {
		String resultRegister = acg.eg.visit(ast.right(), arg);
		String address = acg.av.visit(ast.left(), arg);
		emitStore(resultRegister, 0, address);
		acg.registerPool.release(resultRegister);
		acg.registerPool.release(address);
		return null;
	}

	@Override
	public String builtInWrite(BuiltInWrite ast, Void arg) {
		emitComment("Write: Push 2 arguments to printf: integer and format str");
		String argumentReg = acg.eg.visit(ast.arg(), arg);
		emit("pushl", argumentReg);
		acg.registerPool.release(argumentReg);
		emit("pushl", c("int_format_string"));

		emit("call", Config.PRINTF);

		emitComment("Write: restore stack");
		emitDeallocation(8);
		return null;
	}

	@Override
	public String builtInWriteFloat(BuiltInWriteFloat ast, Void arg) {
		emitComment("WriteF: Load single precision float into FPU");
		String argumentReg = acg.eg.visit(ast.arg(), arg);
		emit("pushl", argumentReg);
		emit("flds", o(0, "%esp"));
		acg.registerPool.release(argumentReg);

		emitComment("WriteF: move double precision float to stack (printf needs it)");
		emitAllocation(8);
		emit("fstpl", o(0, "%esp"));

		emit("pushl", c("float_format_string"));
		emit("call", Config.PRINTF);

		emitComment("WriteF: restore stack");
		emitDeallocation(16);
		return null;
	}

	@Override
	public String builtInWriteln(BuiltInWriteln ast, Void arg) {
		String lineFeedChar = c(10); // ASCI code 10 is line feed
		emit("pushl", lineFeedChar);
		emit("call", Config.PUTCHAR);
		emitDeallocation(4);
		return null;
	}

	@Override
	public String returnStmt(ReturnStmt ast, Void arg) {
		if (ast.arg() != null) {
			String returnReg = acg.eg.visit(ast.arg(), arg);
			emitMove(returnReg, "%eax");
			acg.registerPool.release(returnReg);
			emitMethodSuffix(false);
		} else {
			emitComment("Returning from void...");
			emitMethodSuffix(true);
		}
		return null;
	}

}