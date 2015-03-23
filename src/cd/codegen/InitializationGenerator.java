package cd.codegen;

import static cd.codegen.AssemblerHelper.emit;
import static cd.codegen.AssemblerHelper.emitConstantData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import cd.ir.Ast.ClassDecl;
import cd.ir.AstVisitor;
import cd.ir.Symbol.ClassSymbol;
import cd.ir.Symbol.MethodSymbol;

/**
 * Generator for creating vtables and initializing offset information
 * in the HashMaps about fields in instances and methods in vtables.
 */
class InitializationGenerator extends AstVisitor<Void, Void> {
	private final AstCodeGenerator acg;

	public InitializationGenerator(AstCodeGenerator astCodeGenerator) {
		this.acg = astCodeGenerator;
	}

	/*
	 * Emits vtables and other initializations for all classes.
	 */
	public void go(List<? extends ClassDecl> astRoots) {
		emit("");
		emit(".section .data");
		// Emit vtable for Object type. Is defined as subtype of null
		// because each vtable needs atleast one item (otherwise two vtables
		// will have the same address).
		emitVtable("Object", null, null);
		acg.getClassOffsets("Object");

		// Need vtables for primitive types as well, so we can handle
		// casts from Object to e.g. int[].
		for (String s : new String[]{"float", "boolean", "int", "Object"}) {
			emitVtable(s + "_Array", "Object", null);
		}

		// Generate the rest of the vtables
		for (ClassDecl ast : astRoots) {
			visit(ast, null);
		}

	}
	
	private void emitVtable(String typeName, String superClassName,
						    List<MethodSymbol> orderedMethods) {
		emit(String.format("vtable_%s:", typeName));
		// Set super class to 0 if not needed -- i.e. for Object
		emitConstantData(superClassName == null ? "0" : ("vtable_"+superClassName));
		if (orderedMethods != null) {
			for (MethodSymbol method : orderedMethods) {
				emitConstantData(String.format("%s_%s", method.getClassAst().name, method.name));
			}
		}

	}
	
	@Override
	public Void classDecl(ClassDecl ast, Void arg) {
		List<MethodSymbol> orderedMethods = getMethodsInOrder(ast.sym);

		// Emit vtable
		emitVtable(ast.name, ast.superClass, orderedMethods);
		// instantiate vtable, keep track of offsets for methods, allocate
		// memory on heap
		int offset = 4; // Method pointers in vtable start after the super class pointer
		for (MethodSymbol method : orderedMethods) {
			acg.getClassOffsets(ast.name).addMethod(method.name, offset);
			offset += 4;
		}

		List<String> orderedFields = getFieldsInOrder(ast.sym);
		offset = 4; // Fields in instance start after the vtable pointer
		for (String field : orderedFields) {
			acg.getClassOffsets(ast.name).addField(field, offset);
			offset += 4;
		}

		// Emit vtable for array type. Needed for casting.
		emitVtable(ast.name + "_Array", "Object", null);

		return null;
	}
	
	public List<String> getFieldsInOrder(ClassSymbol sym) {
		// Returns all fields for a class sorted in a predefined way:
		// (fields of furthest superclass alphabetically):(fields of next superclass...):...:(fields of current class alphabetically)
		List<String> fieldInstanceTable = new ArrayList<String>();
		List<String> fields = new ArrayList<String>(sym.fields.keySet());
		Collections.sort(fields);

		if (sym.superClass.name.equals("Object"))
			return fields;
		else {
			fieldInstanceTable.addAll(getFieldsInOrder(sym.superClass));
			Iterator<String> i = fields.iterator();
			while (i.hasNext()) {
				String field = i.next();
				if (fieldInstanceTable.contains(field))
					i.remove();
			}
			fieldInstanceTable.addAll(fields);
			return fieldInstanceTable;
		}
	}

	private Comparator<Object> getToStringComparator() {
		return new Comparator<Object>() {
			public int compare(Object o1, Object o2) {
	           return o1.toString().compareTo(o2.toString());
			}
		};
	}

	public List<MethodSymbol> getMethodsInOrder(ClassSymbol sym) {
		// Returns all methods for a class sorted in a predefined way:
		// (methods of furthest superclass alfabetically):(methods of next superclass...):...:(methods of current class alphabetically)
		List<MethodSymbol> methodInstanceTable = new ArrayList<MethodSymbol>();
		List<MethodSymbol> methods = new ArrayList<MethodSymbol>(sym.methods.values());
		Collections.sort(methods, getToStringComparator());

		if (sym.superClass.name.equals("Object"))
			return methods;
		else {
			methodInstanceTable.addAll(getMethodsInOrder(sym.superClass));
			Iterator<MethodSymbol> i = methods.iterator();
			while (i.hasNext()) {
				MethodSymbol method = i.next();
				int overriddenMethodIndex = methodInstanceTable.indexOf(method);
				if (overriddenMethodIndex >= 0) {
					// Method is overridden, replace superclasses' pointer in vtable with current classes'
					methodInstanceTable.set(overriddenMethodIndex, method);
					i.remove();
				}
			}
			methodInstanceTable.addAll(methods);
			return methodInstanceTable;
		}
	}
}