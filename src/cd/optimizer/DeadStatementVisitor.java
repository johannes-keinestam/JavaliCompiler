package cd.optimizer;

import java.util.List;
import java.util.ListIterator;

import cd.ir.Ast;
import cd.ir.Ast.Assign;
import cd.ir.Ast.BinaryOp;
import cd.ir.Ast.BuiltInRead;
import cd.ir.Ast.BuiltInReadFloat;
import cd.ir.Ast.BuiltInWrite;
import cd.ir.Ast.BuiltInWriteFloat;
import cd.ir.Ast.Cast;
import cd.ir.Ast.ClassDecl;
import cd.ir.Ast.Expr;
import cd.ir.Ast.IfElse;
import cd.ir.Ast.Index;
import cd.ir.Ast.MethodCall;
import cd.ir.Ast.MethodCallExpr;
import cd.ir.Ast.MethodDecl;
import cd.ir.Ast.ReturnStmt;
import cd.ir.Ast.Seq;
import cd.ir.Ast.UnaryOp;
import cd.ir.Ast.Var;
import cd.ir.Ast.WhileLoop;
import cd.ir.AstVisitor;

public class DeadStatementVisitor extends AstVisitor<Void, Void>{
	private StmtContainsVarVisitor scvv = new StmtContainsVarVisitor();
	public void go(List<ClassDecl> astRoots) {
		// for each class, visit each methodbody and calculate variable values
		for (ClassDecl c : astRoots) {
			for (MethodDecl m : c.methods()) {
				visit(m, null);
			}
		}
	}
	@Override
	public Void methodDecl(MethodDecl ast, Void arg) {
		eliminateDeadStatements(ast.body());
		return null;
	}
	
	private void eliminateDeadStatements(Seq s) {
		ListIterator<Ast> li = s.rwChildren.listIterator(0);
		while (li.hasNext()) {
			Ast a = li.next();
			System.out.println(a);
			if (a instanceof Assign && ((Assign)a).left() instanceof Var && !hasSideEffects(((Assign)a).right())) {
				Var lhs = (Var)((Assign)a).left();
				// find next use of var (a.left()). 
				// either another lhs of an assignment
				// or used otherwise
				ListIterator<Ast> li2 = s.rwChildren.listIterator(li.nextIndex());
				while ( li2.hasNext()) {
					Ast a2 = li2.next();
					System.out.print("\t"+a2);
					if (scvv.visit(a2, lhs)) {
						// if a2 contains usage of LHS, we cannot eliminate, so we quit
						System.out.println("  -- DON'T REMOVE PARENT");
						break;
					}
					if (a2 instanceof Assign && ((Assign)a2).left() instanceof Var &&
							((Var)((Assign)a2).left()).name.equals(lhs.name)) {
						// assign to same var, remove previous assign since it has not been used
						li.remove();
						System.out.println("  -- REMOVE PARENT");
						break;
					}
					System.out.println("");
				}
				System.out.println("  -- --");
			} else if (a instanceof WhileLoop) { 
				eliminateDeadStatements((Seq)((WhileLoop) a).body());
			} else if (a instanceof IfElse) {
				eliminateDeadStatements((Seq)((IfElse) a).then());
				if (((IfElse) a).otherwise() instanceof Seq) {
					eliminateDeadStatements((Seq)((IfElse) a).otherwise());
				}
			} else {
				System.out.println("\t NOT REMOVABLE");
			}
		}
		return;

	}
	
	private boolean hasSideEffects(Expr right) {
		// Returns true if Expr might have side effects
		// some expressions may have subexpressions which contain method calls -- check those
		if (right instanceof BinaryOp) {
			BinaryOp bop = (BinaryOp) right;
			return hasSideEffects(bop.left()) || hasSideEffects(bop.right()); 
		} else if (right instanceof Index) {
			Index i = (Index) right;
			return hasSideEffects(i.left()) || hasSideEffects(i.right()); 
		} else if (right instanceof UnaryOp) {
			return hasSideEffects(((UnaryOp) right).arg());
		} else if (right instanceof Cast) {
			return hasSideEffects(((Cast) right).arg());
		}
		// method call expr or reads considered to have possible side effects.
		return right instanceof MethodCallExpr || right instanceof BuiltInReadFloat || right instanceof BuiltInRead;
	}

	class StmtContainsVarVisitor extends AstVisitor<Boolean, Var> {
		@Override
		protected Boolean dflt(Ast ast, Var arg) {
			return false;
		}
		
		@Override
		public Boolean var(Var ast, Var var) {
			return ast.name.equals(var.name);
		}
		@Override
		public Boolean builtInWrite(BuiltInWrite ast, Var var) {
			return visit(ast.arg(), var);
		}
		
		@Override
		public Boolean builtInWriteFloat(BuiltInWriteFloat ast, Var var) {
			return visit(ast.arg(), var);
		}
		
		@Override
		public Boolean assign(Assign ast, Var var) {
			return visit(ast.right(), var);
		}
		
		@Override
		public Boolean binaryOp(BinaryOp ast, Var var) {
			return visit(ast.left(), var) || visit(ast.right(), var);
		}
		
		@Override
		public Boolean unaryOp(UnaryOp ast, Var var) {
			return visit(ast.arg(), var);
		}
		
		@Override
		public Boolean cast(Cast ast, Var var) {
			return visit(ast.arg(), var);
		}
		
		@Override
		public Boolean index(Index ast, Var var) {
			return visit(ast.right(), var) || visit(ast.left(), var);
		}
		
		@Override
		public Boolean ifElse(IfElse ast, Var var) {
			return visit(ast.condition(), var) || visit(ast.then(), var) || visit(ast.otherwise(), var);
		}
		
		@Override
		public Boolean seq(Seq ast, Var var) {
			for (Ast a : ast.children()) {
				if (visit(a, var)) {
					return true;
				}
			}
			return false;
		}
		@Override
		public Boolean methodCall(MethodCall ast, Var var) {
			for (Ast a : ast.allArguments()) {
				if (visit(a, var)) {
					return true;
				}
			}
			return false;
		}
		
		@Override
		public Boolean methodCall(MethodCallExpr ast, Var var) {
			for (Ast a : ast.allArguments()) {
				if (visit(a, var)) {
					return true;
				}
			}
			return false;
		}
		@Override
		public Boolean returnStmt(ReturnStmt ast, Var var) {
			return visit(ast.arg(), var);
		}
		
		@Override
		public Boolean whileLoop(WhileLoop ast, Var var) {
			return visit(ast.condition(), var) || visit(ast.body(), var);
		}
		
	}

}
