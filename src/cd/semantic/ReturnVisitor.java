package cd.semantic;

import cd.ir.Ast;
import cd.ir.AstVisitor;
import cd.ir.Ast.WhileLoop;

import java.util.ArrayList;
import java.util.List;

/**
* Visitor for making sure that all paths in a method return.
* 
* The general algorithm is:
* 	- If any piece of code (represented by a Seq) has a return on the outer level, it is considered to return in all paths.
*   - If not, all branches (or rather, subsequences i.e. all Seq in the Seq: if and while) are checked to return.
*     Only if all subsequences return is the Seq considered to return.
*   Note that there is a small discrepancy between this and the reference compiler, see the README.
*/
class ReturnVisitor extends AstVisitor<Boolean,Void> {

    @Override
    public Boolean classDecl(Ast.ClassDecl ast, Void arg) {
        boolean allMethodsReturn = true;
        for (Ast method : ast.methods())
            allMethodsReturn &= visit(method, null);
        return allMethodsReturn;
    }

    @Override
    public Boolean ifElse(Ast.IfElse ast, Void arg) {
        boolean hasReturn = true;
        hasReturn &= visit(ast.then(),null);
        hasReturn &= visit(ast.otherwise(),null);
        return hasReturn;
    }

    @Override
    public Boolean methodDecl(Ast.MethodDecl ast, Void arg) {
        if (!ast.returnType.equals("void"))
            return visit(ast.body(), null);
        return true;
    }

    @Override
    public Boolean nop(Ast.Nop ast, Void arg) {
        return true;
    }

    @Override
    public Boolean seq(Ast.Seq ast, Void arg) {
        boolean hasReturn = true;
        List<Ast> branches = new ArrayList<Ast>();
        branches.addAll(ast.childrenOfType(Ast.IfElse.class));
        branches.addAll(ast.childrenOfType(Ast.WhileLoop.class));
        if (!ast.childrenOfType(Ast.ReturnStmt.class).isEmpty()) {
            return true;
        }
        if (branches.isEmpty())
            return false;
        for (Ast branch : branches) {
            hasReturn &= visit(branch, null);
        }
        return hasReturn;
    }
    
    @Override
    public Boolean whileLoop(WhileLoop ast, Void arg) {
    	// Reference semantic analyzer will not correctly handle this case
    	return visit(ast.body(), arg);
    }

    @Override
    public Boolean returnStmt(Ast.ReturnStmt ast, Void arg) {
        return true;
    }



}
