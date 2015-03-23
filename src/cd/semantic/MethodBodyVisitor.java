package cd.semantic;

import cd.exceptions.SemanticFailure;
import cd.exceptions.SemanticFailure.Cause;
import cd.ir.Ast;
import cd.ir.AstVisitor;
import cd.ir.Symbol;
import cd.ir.Symbol.MethodSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
* A visitor class for verifying that the method bodies contain semantically correct expressions and statements.
*/
class MethodBodyVisitor extends AstVisitor<Symbol, Ast.MethodDecl> {
    private final Map<String, Symbol.ClassSymbol> classes;

    public MethodBodyVisitor(Map<String, Symbol.ClassSymbol> classes) {
        this.classes = classes;
    }

    @Override
    public Symbol classDecl(Ast.ClassDecl ast, Ast.MethodDecl arg) {
        for (Ast.MethodDecl m : ast.methods())
            visit(m, null);
        return null;
    }

    @Override
    public Symbol methodDecl(Ast.MethodDecl ast, Ast.MethodDecl arg) {
        MethodSymbol overriddenMethod = ast.sym.getClassAst().sym.superClass.getMethod(ast.name);
        MethodSymbol overridingMethod = ast.sym;
        // Verify that method is correctly overridden
		if (overriddenMethod != null) {
			if (overridingMethod.parameters.size() != overriddenMethod.parameters.size())
				throw new SemanticFailure(Cause.INVALID_OVERRIDE, "Method %s is not overridden correctly (wrong number of parameters)", overridingMethod);
			//Rule 2.2.9
			for (int i = 0; i < overridingMethod.parameters.size(); i++){
				if (!overridingMethod.parameters.get(i).type.equals(overriddenMethod.parameters.get(i).type))
					throw new SemanticFailure(Cause.INVALID_OVERRIDE, "Method %s in class %s is not overridden correctly (different parameter types %s and %s for parameter %s)",
							overridingMethod, ast.sym.getClassAst().name, overridingMethod.parameters.get(i).type, overriddenMethod.parameters.get(i).type, overridingMethod.parameters.get(i));
			}
			if ((overridingMethod.returnType == null) || (overriddenMethod.returnType == null)) {
				if (overridingMethod.returnType != overriddenMethod.returnType) {
					throw new SemanticFailure(Cause.INVALID_OVERRIDE, "Method %s is not overridden correctly (different return type)", overridingMethod);
				}
			} else if (!overridingMethod.returnType.equals(overriddenMethod.returnType)) {
				throw new SemanticFailure(Cause.INVALID_OVERRIDE, "Method %s is not overridden correctly (different return type)", overridingMethod);
			}
		}
    	visit(ast.body(), ast);
        return null;
    }

    @Override
    public Symbol ifElse(Ast.IfElse ast, Ast.MethodDecl arg) {
        //Rule 2.3.3
        Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(ast.condition(), arg);
        if (!(argType.toString().equals("boolean")))
			throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Invalid if condition in method %s", arg.name);
        visit(ast.then(), arg);
        visit(ast.otherwise(), arg);
        return null;
    }

    @Override
    public Symbol whileLoop(Ast.WhileLoop ast, Ast.MethodDecl arg) {
        //Rule 2.3.3
        Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(ast.condition(), arg);
        if (!(argType.toString().equals("boolean")))
			throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Invalid while condition in method %s", arg.name);
        visit(ast.body(), arg);
        return null;
    }

    @Override
    public Symbol assign(Ast.Assign ast, Ast.MethodDecl arg) {
        //Rule 2.3.4
        Symbol.TypeSymbol rhs = (Symbol.TypeSymbol)visit(ast.right(), arg);
        Symbol.TypeSymbol lhs = (Symbol.TypeSymbol)visit(ast.left(), arg);
        // Rule 2.3.20
        if (!(
                (ast.left() instanceof Ast.Var) ||
                (ast.left() instanceof Ast.Field) ||
                (ast.left() instanceof Ast.Index)
            )) {
            throw new SemanticFailure(SemanticFailure.Cause.NOT_ASSIGNABLE, "Cannot assign to %s", ast.toString());
        }
         // Rule 2.3.17
        if (rhs instanceof Symbol.NullTypeSymbol && !lhs.isReferenceType())
        	throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot assign null to non-reference type %s", lhs);
        if (lhs.equals(rhs) || rhs instanceof Symbol.NullTypeSymbol)
            return null;
        if ((lhs instanceof Symbol.PrimitiveTypeSymbol || rhs instanceof Symbol.PrimitiveTypeSymbol) ||
                (lhs instanceof Symbol.ArrayTypeSymbol || rhs instanceof Symbol.ArrayTypeSymbol)) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Assignment types invalid, %s != %s", lhs, rhs);
        }
        // Check subtyping relationship
        Symbol.ClassSymbol r = classes.get(rhs.name);
        Symbol.ClassSymbol l = classes.get(lhs.name);
        if (isSubtypeOf(r, l)) {
            if (isSubtypeOf(l, r))
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Circular inheritance");
        } else {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "LHS is not supertype of RHS in assignment %s var = %s", lhs, rhs);
        }
        return null;
    }

    private boolean isSubtypeOf(Symbol.ClassSymbol sub, Symbol.ClassSymbol sup) {
        Symbol.ClassSymbol c = sub;
        if (sup.name.equals("Object"))
            return true;
        while(c.name != "Object"){
            if (c.name.equals(sup.name))
                return true;
            c = c.superClass;
        }
        return false;
    }

    @Override
    public Symbol nullConst(Ast.NullConst ast, Ast.MethodDecl arg) {
    	ast.type = new Symbol.NullTypeSymbol();
    	return ast.type;
    }

    private boolean isValidPolymorphism(Symbol.TypeSymbol sub, Symbol.TypeSymbol sup) {
        if (sub instanceof Symbol.PrimitiveTypeSymbol && sup instanceof Symbol.PrimitiveTypeSymbol) {
            return sub.equals(sup);
        }
        if (sub instanceof Symbol.ArrayTypeSymbol) {
            return sup.name == "Object" || sup.equals(sub);
        }
        if (sub instanceof Symbol.ClassSymbol && sup instanceof Symbol.ClassSymbol) {
            return isSubtypeOf((Symbol.ClassSymbol)sub, (Symbol.ClassSymbol)sup);
        }
        return false;
    }

    @Override
    public Symbol builtInRead(Ast.BuiltInRead ast, Ast.MethodDecl arg) {
        //Rule 2.3.2
        if (!ast.children().isEmpty()) {
            throw new SemanticFailure(SemanticFailure.Cause.WRONG_NUMBER_OF_ARGUMENTS, "Wrong number of arguments to read");
        }
        // Rule 2.3.9
        return new Symbol.PrimitiveTypeSymbol("int");
    }

    @Override
    public Symbol builtInReadFloat(Ast.BuiltInReadFloat ast, Ast.MethodDecl arg) {
        //Rule 2.3.2
        if (!ast.children().isEmpty()) {
            throw new SemanticFailure(SemanticFailure.Cause.WRONG_NUMBER_OF_ARGUMENTS, "Wrong number of arguments to readf");
        }
        // Rule 2.3.9
        return new Symbol.PrimitiveTypeSymbol("float");
    }

    @Override
    public Symbol cast(Ast.Cast ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol argSym = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (ast.typeName.equals("Object") && !(argSym instanceof Symbol.PrimitiveTypeSymbol)) {
        	ast.typeSym = classes.get("Object"); 
            return ast.typeSym; // Can always cast (non-primitive) types to object, even array types
        }
        if (argSym instanceof Symbol.ArrayTypeSymbol) {
            if (ast.typeName.equals(argSym.name)) {
            	String elementTypeName = ast.typeName.split("\\[")[0].trim();
            	ast.typeSym = new Symbol.ArrayTypeSymbol(classes.get(elementTypeName)); 
                return ast.typeSym; // array types can only be cast to/from themselves (or object)
            }
        }
        if (ast.typeName.contains("[") && argSym.name.equals("Object")) {
        	// Trying to cast to array. Check if type is valid.
        	String elementType = ast.typeName.split("\\[")[0].trim();
        	if (classes.containsKey(elementType) || elementType.equals("float") || 
        			elementType.equals("int") || elementType.equals("boolean")) {
        		Symbol.TypeSymbol elSym = classes.containsKey(elementType) ? classes.get(elementType) : new Symbol.PrimitiveTypeSymbol(elementType);
            	ast.typeSym = new Symbol.ArrayTypeSymbol(elSym); 
        		return ast.typeSym;
        	}
        }
        // Rule 2.3.18
        if (!classes.containsKey(ast.typeName))
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_TYPE, "Cannot cast to unknown type %s", ast.typeName);
        // Rule 2.3.10
        if (argSym instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol targetType = classes.get(ast.typeName);
            Symbol.ClassSymbol cSym = (Symbol.ClassSymbol)argSym;
            ast.typeSym = targetType;
            if (isSubtypeOf(cSym, targetType) || isSubtypeOf(targetType, cSym)) {
                return targetType;
            }
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot cast %s to %s -- no relation.", cSym, targetType);
        }
        throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot cast.");
    }

    @Override
    public Symbol newObject(Ast.NewObject ast, Ast.MethodDecl arg) {
        // Rule 2.3.18
        Symbol.ClassSymbol cSym = classes.get(ast.typeName);
        if (cSym == null) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_TYPE, "Cannot instantiate unknown type %s", ast.typeName);
        }
        ast.type = cSym;
        return cSym;
    }

    @Override
    public Symbol var(Ast.Var ast, Ast.MethodDecl arg) {
        // Rule 2.3.19
        Symbol.VariableSymbol v = arg.sym.locals.get(ast.name);
        if (v == null) {
            for (Symbol.VariableSymbol vs : arg.sym.parameters) {
                if (vs.name.equals(ast.name)) {
                    v = vs;
                    break;
                }
            }
        }
        // Correct? Should this resolve to a field if no matching variable exists?
        if (v == null)
            v = classes.get(arg.sym.getClassAst().name).getField(ast.name);
        if (v == null) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_VARIABLE, "No variable %s defined in method %s", ast.name, arg.name);
        }
        ast.type = v.type;
        return v.type;
    }

    @Override
    public Symbol methodCall(Ast.MethodCall ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol rcvr = (Symbol.TypeSymbol)visit(ast.receiver(), arg);
        if (!(rcvr instanceof Symbol.ClassSymbol)) { // Rule 2.3.21
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot call method %s for type %s", ast.methodName, rcvr);
        }
        Symbol.ClassSymbol cRcvr = (Symbol.ClassSymbol)rcvr;
        Symbol.MethodSymbol methodSym = cRcvr.getMethod(ast.methodName);
        // Rule 2.3.14
        if (methodSym == null) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_METHOD, "Type %s does not have method %s", cRcvr.name, ast.methodName);
        }
        ast.sym = methodSym;
        // Rule 2.3.11
        List<Ast.Expr> providedArgs = ast.argumentsWithoutReceiver();
        List<Symbol.VariableSymbol> expectedArgs = methodSym.parameters;
        if (providedArgs.size() != expectedArgs.size()) {
            throw new SemanticFailure(SemanticFailure.Cause.WRONG_NUMBER_OF_ARGUMENTS,
					"Provided %d arguments to %s (expected %d)", providedArgs.size(), ast.methodName, expectedArgs.size());
        }
        // Rule 2.3.12
        for (int i = 0; i < providedArgs.size(); i++) {
            Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(providedArgs.get(i), arg);
            if (!isValidPolymorphism(argType, expectedArgs.get(i).type)) {
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
						"Argument %d to %s has wrong type. Provided: %s, expected: %s", i+1, ast.methodName, argType, expectedArgs.get(i).type);
            }
        }
        return methodSym.returnType;
    }

    @Override
    public Symbol methodCall(Ast.MethodCallExpr ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol rcvr = (Symbol.TypeSymbol)visit(ast.receiver(), arg);
        // Rule 2.3.21
        if (!(rcvr instanceof Symbol.ClassSymbol)) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
					"Cannot call method %s for type %s", ast.methodName, rcvr);
        }
        Symbol.ClassSymbol cRcvr = (Symbol.ClassSymbol)rcvr;
        Symbol.MethodSymbol methodSym = cRcvr.getMethod(ast.methodName);
        // Rule 2.3.14
        if (methodSym == null) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_METHOD,
					"Type %s does not have method %s", cRcvr.name, ast.methodName);
        }
        // Rule 2.3.11
        List<Ast.Expr> providedArgs = ast.argumentsWithoutReceiver();
        List<Symbol.VariableSymbol> expectedArgs = methodSym.parameters;
        if (providedArgs.size() != expectedArgs.size()) {
            throw new SemanticFailure(SemanticFailure.Cause.WRONG_NUMBER_OF_ARGUMENTS,
					"Provided %d arguments to %s (expected %d)", providedArgs.size(), ast.methodName, expectedArgs.size());
        }
        // Rule 2.3.12
        for (int i = 0; i < providedArgs.size(); i++) {
            Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(providedArgs.get(i), arg);
            if (!isValidPolymorphism(argType, expectedArgs.get(i).type)) {
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
						"Argument %d to %s has wrong type. Provided: %s, expected: %s", i+1, ast.methodName, argType, expectedArgs.get(i).type);
            }
        }
        ast.type = methodSym.returnType;
        return ast.type;
    }

    @Override
    public Symbol returnStmt(Ast.ReturnStmt ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol returnType = null;
        if (ast.arg() != null)
            returnType = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (arg.returnType.equals("void") && returnType != null)
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
					"Returning value in method %s with return type void", arg.name);
        Symbol.TypeSymbol expectedReturnType = getTypeSymbol(arg.returnType);
        // Rule 2.3.22
        if (!arg.returnType.equals("void") && !isValidPolymorphism(returnType, expectedReturnType)) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
					"Return type is not subtype of expected return type in method %s", arg.name);
        }
        return null;
    }

    public Symbol.TypeSymbol getTypeSymbol(String type) {
        boolean isArray = false;
        Symbol.TypeSymbol typeSym = null;
        if (type.charAt(type.length()-1) == ']') {
            type = type.split("\\[")[0];
            isArray = true;
        }
        if (type.equals("boolean") || type.equals("int") || type.equals("float")) {
            typeSym = new Symbol.PrimitiveTypeSymbol(type);
        } else {
            typeSym = classes.get(type);
        }
        if (isArray) {
            typeSym = new Symbol.ArrayTypeSymbol(typeSym);
        }
        return typeSym;
    }

    @Override
    public Symbol booleanConst(Ast.BooleanConst ast, Ast.MethodDecl arg) {
    	ast.type = new Symbol.PrimitiveTypeSymbol("boolean");
        return ast.type;
    }
    @Override
    public Symbol floatConst(Ast.FloatConst ast, Ast.MethodDecl arg) {
        ast.type = new Symbol.PrimitiveTypeSymbol("float");
    	return ast.type;
    }
    @Override
    public Symbol intConst(Ast.IntConst ast, Ast.MethodDecl arg) {
    	ast.type = new Symbol.PrimitiveTypeSymbol("int");
    	return ast.type;
    }
    @Override
    public Symbol field(Ast.Field ast, Ast.MethodDecl arg) {
        // Rule 2.3.13
        Symbol.TypeSymbol rcvr = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        // Rule 2.3.21
        if (!(rcvr instanceof Symbol.ClassSymbol)) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot access field from non-object type %s", rcvr);
        }
        Symbol.ClassSymbol currClass = (Symbol.ClassSymbol)rcvr;
        Symbol.VariableSymbol field = currClass.getField(ast.fieldName);
        if (field == null) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_FIELD, "Cannot find field %s in class %s", ast.fieldName, currClass.name);
        }
        field.setStaticClass(currClass);
        ast.sym = field;
        ast.type = rcvr;
        return field.type;
    }

    @Override
    public Symbol thisRef(Ast.ThisRef ast, Ast.MethodDecl arg) {
    	ast.type = classes.get(arg.sym.getClassAst().name);
        return ast.type;
    }

    @Override
    public Symbol index(Ast.Index ast, Ast.MethodDecl arg) {
        // Rule 2.3.15
        Symbol.TypeSymbol arrType = (Symbol.TypeSymbol)visit(ast.left(), arg);
        Symbol.TypeSymbol indexType = (Symbol.TypeSymbol)visit(ast.right(), arg);
        if (!indexType.name.equals("int")) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot get index for array: expected int, got %s", indexType);
        }
        if (!(arrType instanceof Symbol.ArrayTypeSymbol)) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot index on non-array type %s", arrType);
        }
        ast.type =  ((Symbol.ArrayTypeSymbol)arrType).elementType;
        return ast.type;
    }

    @Override
    public Symbol newArray(Ast.NewArray ast, Ast.MethodDecl arg) {
        // Rule 2.3.16
        Symbol.TypeSymbol argSym = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (!argSym.name.equals("int")) {
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Length of non-integer type %s for array", argSym);
        }
        assert ast.typeName.contains("[]") : "Incorrect type name " + ast.typeName;
        String type = ast.typeName.split("\\[")[0];
        if (classes.containsKey(type)) {
            return new Symbol.ArrayTypeSymbol(classes.get(type));
        } else if (type.equals("int") || type.equals("boolean") || type.equals("float")) {
            return new Symbol.ArrayTypeSymbol(new Symbol.PrimitiveTypeSymbol(type));
        }
        throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Cannot create array of type %s", type);
    }

    @Override
    public Symbol builtInWriteFloat(Ast.BuiltInWriteFloat ast, Ast.MethodDecl arg) {
        // Rule 2.3.1
        Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (!argType.name.equals("float"))
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Invalid argument of type %s for writef", argType);
        return null;
    }

    private boolean isRelationalOperator(Ast.BinaryOp.BOp op) {
        return (op == Ast.BinaryOp.BOp.B_GREATER_OR_EQUAL ||
                op == Ast.BinaryOp.BOp.B_GREATER_THAN ||
                op == Ast.BinaryOp.BOp.B_LESS_OR_EQUAL ||
                op == Ast.BinaryOp.BOp.B_LESS_THAN);
    }

    private boolean isBooleanOperator(Ast.BinaryOp.BOp op) {
        return (op == Ast.BinaryOp.BOp.B_AND||
                op == Ast.BinaryOp.BOp.B_OR);
    }

    private boolean isEqualityOperator(Ast.BinaryOp.BOp op) {
        return (op == Ast.BinaryOp.BOp.B_EQUAL||
                op == Ast.BinaryOp.BOp.B_NOT_EQUAL);
    }
    private boolean isArithmeticOperator(Ast.BinaryOp.BOp op) {
        return (op == Ast.BinaryOp.BOp.B_DIV||
                op == Ast.BinaryOp.BOp.B_MINUS||
                op == Ast.BinaryOp.BOp.B_MOD||
                op == Ast.BinaryOp.BOp.B_PLUS||
                op == Ast.BinaryOp.BOp.B_TIMES);
    }
    @Override
    public Symbol binaryOp(Ast.BinaryOp ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol lhs = (Symbol.TypeSymbol)visit(ast.left(), arg);
        Symbol.TypeSymbol rhs = (Symbol.TypeSymbol)visit(ast.right(), arg);
        Symbol.PrimitiveTypeSymbol returnBoolean = new Symbol.PrimitiveTypeSymbol("boolean");
        // Rule 2.3.8
        if (isEqualityOperator(ast.operator)) {
            if (lhs instanceof Symbol.ClassSymbol && rhs instanceof Symbol.ClassSymbol) {
                Symbol.ClassSymbol clhs = (Symbol.ClassSymbol)lhs;
                Symbol.ClassSymbol crhs = (Symbol.ClassSymbol)rhs;
                if (!(isSubtypeOf(clhs, crhs)||isSubtypeOf(crhs,clhs)))
                    throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
							"LHS (%s) or RHS (%s) is not a valid subtype of the other side", clhs, crhs);
                ast.type = returnBoolean;
                return returnBoolean;
            }
            if (lhs instanceof Symbol.ArrayTypeSymbol || rhs instanceof Symbol.ArrayTypeSymbol){
                if(rhs.equals(lhs) || lhs.name.equals("Object") || rhs.name.equals("Object")){
                	ast.type = returnBoolean;
                    return returnBoolean;
                }
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Expression using array is faulty");
            }
            if(lhs.equals(rhs)){
            	ast.type = returnBoolean;
                return returnBoolean;
            }
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,"Primitive type equality mismatch");
        }
        // For all other operators, check that sides are of same type
        if (!rhs.equals(lhs))
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Type of LHS and RHS are not equal");
        // At this point we know that rhs.type == lhs.type, thus we only need to check on one of the sides
        // Rule 2.3.5
        if (isArithmeticOperator(ast.operator)) {
            if (!lhs.name.equals("int") && !lhs.name.equals("float"))
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
						"Type of LHS and RHS is not float or int in arithmetic operation");
            if (ast.operator == Ast.BinaryOp.BOp.B_MOD && lhs.name.equals("float"))
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR,
						"Modulus cannot be applied to float arguments");
            ast.type = lhs;
            return lhs;
        }
        // Rule 2.3.6
        if (isBooleanOperator(ast.operator)) {
            if (!lhs.name.equals("boolean"))
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Boolean operator applied to non-boolean arguments");
            ast.type = lhs;
            return lhs;
        }
        // Rule 2.3.7
        if (isRelationalOperator(ast.operator)) {
            if (!lhs.name.equals("int") && !lhs.name.equals("float"))
                throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Type of LHS and RHS is not float or int in relational operation");
            ast.type = returnBoolean;
            return returnBoolean;
        }
        throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Unknown error in binary operation.");
    }

    @Override
    public Symbol unaryOp(Ast.UnaryOp ast, Ast.MethodDecl arg) {
        Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (ast.operator == Ast.UnaryOp.UOp.U_BOOL_NOT) {
            if (argType.name.equals("boolean")){
                ast.type = argType;
            	return argType;
            }
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "! operator applied to non-boolean expression");
        }
        if (argType.name.equals("int") || argType.name.equals("float")) {
            ast.type = argType;
        	return argType;
        }
        throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Unary arithmetic operator applied to non-int/float type.");
    }

    @Override
    public Symbol builtInWrite(Ast.BuiltInWrite ast, Ast.MethodDecl arg) {
        // Rule 2.3.1
        Symbol.TypeSymbol argType = (Symbol.TypeSymbol)visit(ast.arg(), arg);
        if (!argType.toString().equals("int"))
            throw new SemanticFailure(SemanticFailure.Cause.TYPE_ERROR, "Invalid argument of type %s for write", argType);
        return null;
    }

    @Override
    public Symbol builtInWriteln(Ast.BuiltInWriteln ast, Ast.MethodDecl arg) {
        //Rule 2.3.2
        if (!ast.children().isEmpty()) {
            throw new SemanticFailure(SemanticFailure.Cause.WRONG_NUMBER_OF_ARGUMENTS, "Wrong number of arguments to writeln");
        }
        return null;
    }

}
