package cd.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Symbol {
	
	public final String name;
	
	public static abstract class TypeSymbol extends Symbol {
		
		public TypeSymbol(String name) {
			super(name);
		}

		public abstract boolean isReferenceType();
		
		public String toString() {
			return name;
		}
		
		@Override
		public boolean equals(Object obj) {
			TypeSymbol other = (TypeSymbol)obj;
			return this.name.equals(other.name) && this.isReferenceType() == other.isReferenceType(); 
		}
		
	}
	
	public static class PrimitiveTypeSymbol extends TypeSymbol {
		public PrimitiveTypeSymbol(String name) {
			super(name);
		}		
		
		public boolean isReferenceType() {
			return false;
		}
	}
	
	public static class ArrayTypeSymbol extends TypeSymbol {
		public final TypeSymbol elementType;
		
		public ArrayTypeSymbol(TypeSymbol elementType) {
			super(elementType.name+"[]");
			this.elementType = elementType;
		}
		
		public boolean isReferenceType() {
			return true;
		}
	}
	
	public static class ClassSymbol extends TypeSymbol {
		public final Ast.ClassDecl ast;
		public ClassSymbol superClass;
		public final VariableSymbol thisSymbol =
			new VariableSymbol("this", this);
		public final Map<String, VariableSymbol> fields = 
			new HashMap<String, VariableSymbol>();
		public final Map<String, MethodSymbol> methods =
			new HashMap<String, MethodSymbol>();

		public ClassSymbol(Ast.ClassDecl ast) {
			super(ast.name);
			this.ast = ast;
		}
		
		/** Used to create the default {@code Object} 
		 *  and {@code <null>} types */
		public ClassSymbol(String name) {
			super(name);
			this.ast = null;
		}
		
		public boolean isReferenceType() {
			return true;
		}
		
		public VariableSymbol getField(String name) {
			VariableSymbol fsym = fields.get(name);
			if (fsym == null && superClass != null)
				return superClass.getField(name);
			return fsym;
		}
		
		public MethodSymbol getMethod(String name) {
			MethodSymbol msym = methods.get(name);
			if (msym == null && superClass != null)
				return superClass.getMethod(name);
			return msym;
		}
	}

	public static class MethodSymbol extends Symbol {
		
		public final Ast.MethodDecl ast;
		private Ast.ClassDecl classAst;
		public final Map<String, VariableSymbol> locals =
			new HashMap<String, VariableSymbol>();
		public final List<VariableSymbol> parameters =
			new ArrayList<VariableSymbol>();
		
		public TypeSymbol returnType;
		
		public MethodSymbol(Ast.MethodDecl ast) {
			super(ast.name);
			this.ast = ast;
		}
		
		public String toString() {
			return name + "(...)";
		}

		public Ast.ClassDecl getClassAst() {
			assert classAst != null;
			return classAst;
		}

		public void setClassAst(Ast.ClassDecl classAst) {
			this.classAst = classAst;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MethodSymbol)
				return obj.toString().equals(this.toString());
			return false;
		}
	}
	
	public static class VariableSymbol extends Symbol {
		
		public static enum Kind { PARAM, LOCAL, FIELD };
		public final TypeSymbol type;
		public final Kind kind;
		private ClassSymbol staticClass;
		
		public VariableSymbol(String name, TypeSymbol type) {
			this(name, type, Kind.PARAM);
		}

		public VariableSymbol(String name, TypeSymbol type, Kind kind) {
			super(name);
			this.type = type;
			this.kind = kind;
		}
		
		public ClassSymbol getStaticClass() {
			return staticClass;
		}
		
		public void setStaticClass(ClassSymbol staticClass) {
			this.staticClass = staticClass;
		}
		
		public String toString() {
			return name;
		}
	}

	protected Symbol(String name) {
		this.name = name;
	}
	
	public static class NullTypeSymbol extends TypeSymbol {
		public NullTypeSymbol() {
			super("null");
		}		
		
		public boolean isReferenceType() {
			return true;
		}
	}


}
