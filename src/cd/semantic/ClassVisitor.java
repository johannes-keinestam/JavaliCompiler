package cd.semantic;

import cd.exceptions.SemanticFailure;
import cd.ir.Ast;
import cd.ir.AstVisitor;
import cd.ir.Symbol;
import cd.ir.Symbol.VariableSymbol;
import cd.util.Pair;

import java.util.HashSet;
import java.util.Map;

/**
* A visitor that initializes the class symbols with information about their methods and fields. This will in turn
 * be used by other semantic checking visitors to reason about the program correctness (in method calls, for example).
*/
class ClassVisitor extends AstVisitor<Symbol, Void> {
    private final Map<String, Symbol.ClassSymbol> classes;

    public ClassVisitor(Map<String, Symbol.ClassSymbol> classes) {
        this.classes = classes;
    }

    @Override
    public Symbol classDecl(Ast.ClassDecl c, Void arg) {
        Symbol.ClassSymbol cSym = classes.get(c.name);
        for (Ast.VarDecl f : c.fields()) {
            //Rule 2.2.6, double declaration
            if(cSym.fields.containsKey(f.name))
            	throw new SemanticFailure(SemanticFailure.Cause.DOUBLE_DECLARATION, "Field %s in class %s cannot be redeclared.", f.name, c.name);
            cSym.fields.put(f.name, new Symbol.VariableSymbol(f.name, getTypeSymbol(f.type), Symbol.VariableSymbol.Kind.FIELD));
        }
        for (Ast.MethodDecl m : c.methods()) {
            //Rule 2.2.7, double declaration
            if(cSym.methods.containsKey(m.name))
            	throw new SemanticFailure(SemanticFailure.Cause.DOUBLE_DECLARATION, "Method %s in class %s cannot be redeclared.", m.name, c.name);
            Symbol.MethodSymbol ms = (Symbol.MethodSymbol)visit(m, null);
            ms.setClassAst(c);
            m.sym = ms;
            cSym.methods.put(m.name, ms);
        }
        // Rule 2.2.2
        if (!classes.containsKey(c.superClass)) {
            throw new SemanticFailure(SemanticFailure.Cause.NO_SUCH_TYPE, "Superclass %s for %s does not exist", c.superClass, c.name);
        }
        cSym.superClass = classes.get(c.superClass);
        c.sym = cSym;
        return cSym;
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
    public Symbol methodDecl(Ast.MethodDecl m, Void arg) {
        Symbol.MethodSymbol mSym = new Symbol.MethodSymbol(m);
        //Rule 2.2.10
        if ((new HashSet<String>(m.argumentNames)).size() != m.argumentNames.size())
            throw new SemanticFailure(SemanticFailure.Cause.DOUBLE_DECLARATION, "Duplicate parameter name in method %s", mSym);
        for (Pair<String> p : Pair.zip(m.argumentNames, m.argumentTypes)) {
            mSym.parameters.add(new Symbol.VariableSymbol(p.a, getTypeSymbol(p.b)));
        }
        for (Ast local : m.decls().children()) {
            String name = ((Ast.VarDecl)local).name;
            String type = ((Ast.VarDecl)local).type;
            // Rule 2.2.11
            if (mSym.locals.containsKey(name))
                throw new SemanticFailure(SemanticFailure.Cause.DOUBLE_DECLARATION, "Duplicate local variables in method %s", mSym);
            VariableSymbol vSym = new Symbol.VariableSymbol(name, getTypeSymbol(type), Symbol.VariableSymbol.Kind.LOCAL);
            ((Ast.VarDecl)local).sym = vSym;
            mSym.locals.put(name, vSym);
        }
        mSym.returnType = getTypeSymbol(m.returnType);

        // Rule.2.3.23
        ReturnVisitor returnVisitor = new ReturnVisitor();
        boolean allPathsReturn = returnVisitor.visit(m, null);
        if (!allPathsReturn)
            throw new SemanticFailure(SemanticFailure.Cause.MISSING_RETURN, "Not all paths in method %s have return statements", m.name);
        return mSym;
    }


}
