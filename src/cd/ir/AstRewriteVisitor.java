package cd.ir;

import java.util.ListIterator;

public class AstRewriteVisitor<A> extends AstVisitor<Ast, A> {

	@Override
	public Ast visitChildren(Ast ast, A arg) {
		ListIterator<Ast> children = ast.rwChildren.listIterator();
		while (children.hasNext()) {
			Ast child = children.next();
			if (child != null) {
				Ast replace = visit(child, arg);
				if (replace != child)
					children.set(replace);
			}
		}
		return ast;
	}

}
