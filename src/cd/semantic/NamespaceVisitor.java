package cd.semantic;

import cd.exceptions.SemanticFailure;
import cd.ir.Ast;
import cd.ir.AstVisitor;
import cd.ir.Symbol;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple visitor which initializes the namespace by producing bare-bones class symbols.
 */
public class NamespaceVisitor extends AstVisitor<Symbol.ClassSymbol, Void> {
    public final Map<String, Symbol.ClassSymbol> initializedClasses;

    public NamespaceVisitor() {
        initializedClasses = new HashMap<String, Symbol.ClassSymbol>();
        // Initializing Object so it can be used as the top-level super class.
        initializedClasses.put("Object", new Symbol.ClassSymbol("Object"));
    }

    /**
     * Visits the class declaration: produces the class symbol and checks for class declaration errors.
     */
    @Override
    public Symbol.ClassSymbol classDecl(Ast.ClassDecl c, Void arg) {
        //Rule 2.2.4, object should not be a class
        if (c.name.equals("Object"))
            throw new SemanticFailure(SemanticFailure.Cause.OBJECT_CLASS_DEFINED, "Object class is not allowed to be defined.");
        //Rule 2.2.5, double declaration
        if (initializedClasses.containsKey(c.name))
            throw new SemanticFailure(SemanticFailure.Cause.DOUBLE_DECLARATION, "Not allowed to redefine existing class %s", c.name);

        Symbol.ClassSymbol sym = new Symbol.ClassSymbol(c);
        initializedClasses.put(c.name, sym);
        return sym;
    }
}
