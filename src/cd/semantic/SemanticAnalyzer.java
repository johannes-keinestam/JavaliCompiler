package cd.semantic;

import java.util.List;
import java.util.Map;

import cd.Main;
import cd.exceptions.SemanticFailure;
import cd.exceptions.SemanticFailure.Cause;
import cd.ir.Ast.ClassDecl;
import cd.ir.Symbol.ClassSymbol;

public class SemanticAnalyzer {
	
	public final Main main;
	
	public SemanticAnalyzer(Main main) {
		this.main = main;
	}

	/**
	 * Performs a semantic check of the program.
	 *
	 * @throws SemanticFailure if the semantics of the program is faulty.
	 */
	public void check(List<ClassDecl> classDecls) throws SemanticFailure {
		// Initialize the namespace by visiting all classes. Necessary for checking for references to non-existing types,
		// incorrect polymorphism, etc.
		NamespaceVisitor namespaceVisitor = new NamespaceVisitor();
		for (ClassDecl c : classDecls) {
			namespaceVisitor.visit(c, null);
		}
		Map<String, ClassSymbol> classes = namespaceVisitor.initializedClasses;

		// Visit the classes in a second pass, to extend the types (classes) with their interface:
		// methods (with parameters, return types and locals) and fields. Provide the visitor with the namespace produced
		// by the previous visitor.
		ClassVisitor classVisitor = new ClassVisitor(classes);
		for (ClassSymbol c : classes.values()) {
			// Do not visit the dummy "Object" class symbol, since it is not an actual AST node.
			if (!c.name.equals("Object"))
				classVisitor.visit(c.ast, null);
		}

		// Verify that the namespace is correct.
		verifyNamespaceSemantics(classes);

		// Verify that the defined method bodies are semantically correct.
		MethodBodyVisitor methodBodyVisitor = new MethodBodyVisitor(classes);
		for (ClassSymbol c : classes.values()) {
			// Do not visit the dummy "Object" class symbol, since it is not an actual AST node.
			if (!c.name.equals("Object"))
				methodBodyVisitor.visit(c.ast, null);
		}
		
	}

	/**
	 * Some of the semantic rules can only be verified by regarding the whole namespace and signatures. This method
	 * verifies that the given program upholds these rules.
	 */
	private void verifyNamespaceSemantics(Map<String, ClassSymbol> classes) {
		// Rule 2.2.1: Verify that there is a valid starting point for the program:
		// a Main class with main method without parameters.
		if (!(classes.containsKey("Main") &&
				classes.get("Main").methods.containsKey("main") &&
				classes.get("Main").methods.get("main").parameters.isEmpty())) {
			throw new SemanticFailure(Cause.INVALID_START_POINT,
					"No valid starting point defined for program. Expected Main.main with 0 parameters.");
		}

		// Loop over all classes and their superclasses to check for circular inheritance.
		for(ClassSymbol c : classes.values()){
			ClassSymbol startingClass = c;
			if (c.name.equals("Object"))
				continue;
			while(!(c.superClass.name.equals("Object"))){
				// Rule 2.2.3: Circular Inheritance
				if(c.superClass.name.equals(startingClass.name))
					throw new SemanticFailure(Cause.CIRCULAR_INHERITANCE);
				c = c.superClass;
			}
		}
	}

}
