package cd.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cd.debug.AstOneLine;
import cd.ir.Ast;
import cd.ir.Ast.Assign;
import cd.ir.Ast.BinaryOp;
import cd.ir.Ast.BooleanConst;
import cd.ir.Ast.BuiltInWrite;
import cd.ir.Ast.BuiltInWriteFloat;
import cd.ir.Ast.ClassDecl;
import cd.ir.Ast.Expr;
import cd.ir.Ast.FloatConst;
import cd.ir.Ast.IfElse;
import cd.ir.Ast.Index;
import cd.ir.Ast.IntConst;
import cd.ir.Ast.MethodCall;
import cd.ir.Ast.MethodCallExpr;
import cd.ir.Ast.MethodDecl;
import cd.ir.Ast.NewArray;
import cd.ir.Ast.Seq;
import cd.ir.Ast.UnaryOp;
import cd.ir.Ast.Var;
import cd.ir.Ast.WhileLoop;
import cd.ir.AstVisitor;

public class ConstantFolderVisitor extends
		AstVisitor<Object, Map<String, Object>> {
	
	ValueUpdaterVisitor cvrv = new ValueUpdaterVisitor();

	public void go(List<ClassDecl> astRoots) {
		// for each class, visit each methodbody and calculate variable values
		for (ClassDecl c : astRoots) {
			for (MethodDecl m : c.methods()) {
				Map<String, Object> foldMap = new HashMap<String, Object>();
				visit(m, foldMap);
			}
		}
	}

	@Override
	public Void visit(Ast ast, Map<String, Object> arg) {
		System.out.print("\t"+AstOneLine.toString(ast));
		Object v = super.visit(ast, arg);
		System.out.println("\t"+arg.toString());
		return (Void)v;
	}

	@Override
	public Void methodDecl(MethodDecl ast, Map<String, Object> arg) {
		System.out.println(ast.name+":");
		visit(ast.body(), arg);
		return null;
	}

	@Override
	public Void assign(Assign ast, Map<String, Object> arg) {
		Expr right = ast.right();
		Expr lhs = ast.left();
		visit(lhs, arg);
		String name;
		if (lhs instanceof Var) {
			name = ((Var) lhs).name;
		} else {
			return null;
		}
		Object rhsValue = visit(right,arg);
		if(rhsValue!= null){
			arg.put(name, rhsValue);
			ast.setRight(newNodeFromConstant(rhsValue));
		}
		return null;
	}
	
	private Expr newNodeFromConstant(Object constant) {
		if (constant instanceof Integer) {
			return new IntConst(((Integer) constant).intValue());
		}
		if (constant instanceof Float) {
			return new FloatConst(((Float) constant).floatValue());
		}
		if (constant instanceof Boolean) {
			return new BooleanConst(((Boolean) constant).booleanValue());
		}
		throw new ClassCastException("what unknown type "+constant.getClass());
	}
	
	@Override
	public Void builtInWrite(BuiltInWrite ast, Map<String, Object> arg) {
		Object argValue = visit(ast.arg(), arg);
		if(argValue != null){
			ast.setArg(newNodeFromConstant(argValue));
		}
		return null;
	}
	@Override
	public Void builtInWriteFloat(BuiltInWriteFloat ast, Map<String, Object> arg) {
		Object argValue = visit(ast.arg(), arg);
		if(argValue != null){
			ast.setArg(newNodeFromConstant(argValue));
		}
		return null;
	}
	
	@Override
	public Void index(Index ast, Map<String, Object> arg) {
		Object argValue = visit(ast.right(), arg);
		if(argValue != null){
			ast.setRight(newNodeFromConstant(argValue));
		}
		return null;
	}
	
	@Override
	public Void newArray(NewArray ast, Map<String, Object> arg) {
		Object argValue = visit(ast.arg(), arg);
		if(argValue != null){
			ast.setArg(newNodeFromConstant(argValue));
		}
		return null;
	}
	
	@Override
	public Void methodCall(MethodCall ast, Map<String, Object> arg) {
		List<Ast> newArgumentList = new ArrayList<Ast>();
		for (Expr param : ast.argumentsWithoutReceiver()) {
			Object newParam = visit(param, arg);
			if (newParam != null)
				newArgumentList.add(newNodeFromConstant(newParam));
			else
				newArgumentList.add(param);
		}
		ast.setArguments(newArgumentList);
		return null;
	}
	
	@Override
	public Void methodCall(MethodCallExpr ast, Map<String, Object> arg) {
		List<Ast> newArgumentList = new ArrayList<Ast>();
		for (Expr param : ast.argumentsWithoutReceiver()) {
			Object newParam = visit(param, arg);
			if (newParam != null)
				newArgumentList.add(newNodeFromConstant(newParam));
			else
				newArgumentList.add(param);
		}
		ast.setArguments(newArgumentList);
		return null;
	}
		
	@Override
	protected Object dfltExpr(Expr ast, Map<String, Object> arg) {
		return cvrv.visit(ast, arg);
	}
	
	@Override
	public Void ifElse(IfElse ast, Map<String, Object> arg) {
		Boolean val = (Boolean) visit(ast.condition(), arg);

		if (val == null) {
			Map<String, Object> ifState = new HashMap<String, Object>(arg);
			Map<String, Object> elseState = new HashMap<String, Object>(arg);
			visit(ast.then(), ifState);
			if (ast.otherwise() instanceof Seq)
				visit(ast.otherwise(), elseState);
			// Not known which block is run -- assigned variables in
			// then and otherwise blocks are now in an unknown state.
			Set<String> assignedVars = assignedVarsInLoop((Seq)ast.then());
			if (ast.otherwise() instanceof Seq)
				assignedVars.addAll(assignedVarsInLoop((Seq)ast.otherwise()));
			for (String assignedVarName : assignedVars) {
				System.out.println("Removing arg " +assignedVarName);
				arg.remove(assignedVarName);
			}
			// TODO: What if assignment of var V is in both then and otherwise,
			// and the RHS is the same constant? Should not remove value.
			return null;
		}
		if (val.booleanValue() == true) {
			// Then Block guaranteed to run. Update constants as used.
			for (Ast child : ast.then().children()) {
				if (child instanceof IfElse || child instanceof WhileLoop) {
					visit(child, arg);
					continue;
				} else if (!(child instanceof Assign)) {
					continue;
				}
				Assign as = (Assign)child;
				if (as.left() instanceof Var) {
					Object rhsValue = visit(as.right(), arg);
					String childName = ((Var) as.left()).name;
					if (rhsValue != null) {
						arg.put(childName, rhsValue);
						as.setRight(newNodeFromConstant(rhsValue));
					}
				}
			}
		} else {
			// Otherwise Block guaranteed to run. Update constants as used.
			for (Ast child : ast.otherwise().children()) {
				if (child instanceof IfElse || child instanceof WhileLoop) {
					visit(child, arg);
				} else if (!(child instanceof Assign)) {
					continue;
				}
				Assign as = (Assign)child;
				if (as.left() instanceof Var) {
					Object rhsValue = visit(as.right(), arg);
					String childName = ((Var) as.left()).name;
					if (rhsValue != null) {
						arg.put(childName, rhsValue);
						as.setRight(newNodeFromConstant(rhsValue));
					}
				}
			}

		}
		return null;
	}
	
	@Override
	public Void whileLoop(WhileLoop ast, Map<String, Object> arg) {
		Boolean cond = (Boolean) visit(ast.condition(), arg);
		if (cond != null && cond.booleanValue() == false) {
			return null; // While body will never run.
		}
		// We need to consider all variables to have an undefined
		// state in a while loop iff they are ever assigned to.
		for (String varName : assignedVarsInLoop((Seq)ast.body())) {
			arg.remove(varName);
		}
		// However, if they are assigned to constants, we can populate
		// them back here.
		for (Ast a : ast.body().children()) {
			if (a instanceof IfElse || a instanceof WhileLoop) {
				visit(a, arg);
				continue;
			} else if (!(a instanceof Assign)) {
				continue;
			}
			Assign as = (Assign)a;
			if (as.left() instanceof Var) {
				Var assignedVar = (Var)as.left();
				if (cond != null && as.right() instanceof IntConst) {
					arg.put(assignedVar.name, ((IntConst)as.right()).value);
				} else if (cond != null && as.right() instanceof FloatConst) {
					arg.put(assignedVar.name, ((FloatConst)as.right()).value);
				} else if (cond != null && as.right() instanceof BooleanConst) {
					arg.put(assignedVar.name, ((BooleanConst)as.right()).value);
				} else {
					Object value = visit(as.right(), arg);
					if (value != null) {
						arg.put(assignedVar.name, value);
						as.setRight(newNodeFromConstant(value)); // TODO: Test this.
					}
				}
			}

		}
		return null;
	}
	
	private Set<String> assignedVarsInLoop(Seq ast) {
		Set<String> vars = new HashSet<String>();
		for (Ast a : ast.children()) {
			if (a instanceof WhileLoop)
				vars.addAll(assignedVarsInLoop((Seq)((WhileLoop) a).body()));
			else if (a instanceof IfElse) {
				vars.addAll(assignedVarsInLoop((Seq)((IfElse) a).then()));
				if (((IfElse) a).otherwise() instanceof Seq) {
					vars.addAll(assignedVarsInLoop((Seq)((IfElse) a).otherwise()));
				}
			} else if (a instanceof Assign && ((Assign)a).left() instanceof Var) {
				vars.add(((Var)((Assign)a).left()).name);
			}
		}
		return vars;
	}

	/**
	 * Returns value of expression given constant or variable expression.
	 * Will also resolve binary or unary expressions containing these.
	 */
	private class ValueUpdaterVisitor extends AstVisitor<Object, Map<String,Object>> {
		@Override
		protected Object dflt(Ast ast, Map<String, Object> arg) {
			return null;
		}
		@Override
		public Object intConst(IntConst ast, Map<String,Object> knownValues) {
			return ast.value;
		}
		@Override
		public Object booleanConst(BooleanConst ast, Map<String,Object> knownValues) {
			return ast.value;
		}
		@Override
		public Object floatConst(FloatConst ast, Map<String,Object> knownValues) {
			return ast.value;
		}
		@Override
		public Object var(Var ast, Map<String,Object> knownValues) {
			return knownValues.get(ast.name);
		}
		@Override
		public Object unaryOp(UnaryOp ast, Map<String,Object> knownValues) {
			Object arg = visit(ast.arg(),knownValues);
			if(arg == null)
				return null;
			switch (ast.operator){
			case U_BOOL_NOT:
				return !((Boolean)arg).booleanValue();
			case U_MINUS:
				if (arg instanceof Integer)
					return -((Integer)arg).intValue();
				if (arg instanceof Float)
					return -((Float)arg).floatValue();
			case U_PLUS:
				return arg;
			}
			return null;
		}
		@Override
		public Object binaryOp(BinaryOp ast, Map<String,Object> knownValues) {
			Object lhs = visit(ast.left(), knownValues);
			Object rhs = visit(ast.right(), knownValues);
			if (lhs == null || rhs == null) {
				return null;
			}
			switch (ast.operator) {
			case B_PLUS:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() + ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() + ((Float)rhs).floatValue();
			case B_MINUS:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() - ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() - ((Float)rhs).floatValue();
			case B_TIMES:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() * ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() * ((Float)rhs).floatValue();
			case B_DIV:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() / ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() / ((Float)rhs).floatValue();
			case B_MOD:
				return ((Integer)lhs).intValue() % ((Integer)rhs).intValue();
			case B_OR:
				return ((Boolean)lhs).booleanValue() || ((Boolean)rhs).booleanValue();
			case B_AND:
				return ((Boolean)lhs).booleanValue() && ((Boolean)rhs).booleanValue();
			case B_EQUAL:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() == ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() == ((Float)rhs).floatValue();
				if(lhs instanceof Boolean)
					return ((Boolean)lhs).booleanValue() == ((Boolean)rhs).booleanValue();
			case B_NOT_EQUAL:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() != ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() != ((Float)rhs).floatValue();
				if(lhs instanceof Boolean)
					return ((Boolean)lhs).booleanValue() != ((Boolean)rhs).booleanValue();
			case B_LESS_OR_EQUAL:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() <= ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() <= ((Float)rhs).floatValue();
			case B_GREATER_OR_EQUAL:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() >= ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() >= ((Float)rhs).floatValue();
			case B_LESS_THAN:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() < ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() < ((Float)rhs).floatValue();
			case B_GREATER_THAN:
				if (lhs instanceof Integer)
					return ((Integer)lhs).intValue() > ((Integer)rhs).intValue();
				if (lhs instanceof Float)
					return ((Float)lhs).floatValue() > ((Float)rhs).floatValue();
			default:
				break;
			}
			// TODO: consider c + 5 + 5. Should evaluate to c + 10, not just return.
			return null;
		}
	}
}
