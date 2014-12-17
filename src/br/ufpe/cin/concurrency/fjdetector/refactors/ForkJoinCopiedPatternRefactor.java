package br.ufpe.cin.concurrency.fjdetector.refactors;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import br.ufpe.cin.concurrency.fjdetector.Refactor;

public class ForkJoinCopiedPatternRefactor implements Refactor {

	private final ASTRewrite rewriter;

	public ForkJoinCopiedPatternRefactor(ASTRewrite rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public void refactor(ASTNode node) {
		TypeDeclaration clazz = ((TypeDeclaration) node);
		
		FieldDeclaration lastField = getLastFieldDeclared(clazz);
		
		FieldDeclaration from = createNewField(node, "from");
		FieldDeclaration to = createNewField(node, "to");
		
		ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertAfter(from, lastField, null);
		listRewrite.insertAfter(to, from, null);
		
		MethodDeclaration constructor = createPrivateConstructor(node);
		listRewrite.insertAfter(constructor, to, null);
		
		for (MethodDeclaration method : clazz.getMethods()) {
			if (!method.isConstructor() && 
					method.parameters().size() == 0 && 
					method.getName().getIdentifier().equals("compute")) {
				
				List stms = method.getBody().statements();
				for (Object o: stms) {
					Statement s = (Statement) o;
					if (s instanceof IfStatement) {
		                IfStatement ifstmt = (IfStatement) s;

		                Expression ife = ifstmt.getExpression();
//		                analyzeSequentialCase(ife);
		                
		                Statement elsestmt = ifstmt.getElseStatement();
		                ASTNode newElseStm = refactorElseStatement(elsestmt);
		                rewriter.replace(elsestmt, newElseStm, null);
					}
				}
			}
		}
	}

	private ASTNode refactorElseStatement(ASTNode elseStm) {
		AST ast = elseStm.getAST();
		ASTNode newElseStm = ASTNode.copySubtree(elseStm.getAST(), elseStm);
		
		if (newElseStm instanceof IfStatement) {
			IfStatement then = (IfStatement) newElseStm;
			if (then.getThenStatement() instanceof Block) {
				Block block = ((Block) then.getThenStatement());
				for(Object stm: block.statements()) {
					if(stm instanceof VariableDeclarationStatement) {
						List vFrags = ((VariableDeclarationStatement) stm).fragments();
		                for (Object frag : vFrags) {
		                    Expression e = ((VariableDeclarationFragment) frag).getInitializer();
		                    if(isSplitExpression(e)) {
//		                    	InfixExpression infixExpression = ((InfixExpression) e);
		                    	
//		                    	Expression left = infixExpression.getLeftOperand();
//		                        Expression right = infixExpression.getRightOperand();
//		                        Operator op = infixExpression.getOperator();
		                        
		                    	
		                        InfixExpression splitExpression = ast.newInfixExpression();
		                        ParenthesizedExpression pExpression = ast.newParenthesizedExpression();
		                        
		                        InfixExpression andExpression = ast.newInfixExpression();
		                        andExpression.setLeftOperand(ast.newSimpleName("from"));
		                        andExpression.setOperator(Operator.PLUS);
		                        andExpression.setRightOperand(ast.newSimpleName("to"));
		                        pExpression.setExpression(andExpression);
		                        
		                        splitExpression.setRightOperand(ast.newNumberLiteral("2"));
		                        splitExpression.setLeftOperand(pExpression);
		                        splitExpression.setOperator(Operator.DIVIDE);
		                        
		                    	System.out.println(splitExpression);
		                    	
		                    	rewriter.replace(e, splitExpression, null);
		                    }
		                }
					}
				}
//				block.statements().remove(0);
//				System.out.println(block);
				return newElseStm;
			}
		}
		throw new UnsupportedOperationException("It is not an Else block!!\n" + elseStm);
	}

	private boolean isSplitExpression(Expression e) {
		if (e instanceof InfixExpression) {
            Operator op = ((InfixExpression) e).getOperator();
            if (op.equals(Operator.DIVIDE)) return true;
        }	
		return false;
	}

	private MethodDeclaration createPrivateConstructor(ASTNode node) {
		AST ast = node.getAST();
		TypeDeclaration clazz = ((TypeDeclaration) node);
		for(MethodDeclaration method: clazz.getMethods()) {
			if(method.isConstructor()) {
		
				// remove public modifier
				method.modifiers().remove(0);
				method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

				// constructor parameters
				SingleVariableDeclaration from = createNewParam(node, "from");
				method.parameters().add(from);

				SingleVariableDeclaration to = createNewParam(node, "to");
				method.parameters().add(to);

				Block block = method.getBody();
				createNewAssignment(node, block, "from");
				createNewAssignment(node, block, "to");

				return method;
			}
		}
		throw new UnsupportedOperationException(String.format("Class %s does not have a constructor definer!", clazz));
	}

	private void createNewAssignment(ASTNode node, Block block, String varName) {
		AST ast = node.getAST();
		Assignment a = ast.newAssignment();
		a.setOperator(Assignment.Operator.ASSIGN);
		block.statements().add(ast.newExpressionStatement(a));
		FieldAccess fa = ast.newFieldAccess();
		fa.setExpression(ast.newThisExpression());
		fa.setName(ast.newSimpleName(varName));
		a.setLeftHandSide(fa);
		a.setRightHandSide(ast.newSimpleName(varName));
	}

	private SingleVariableDeclaration createNewParam(ASTNode node, String name) {
		AST ast = node.getAST();
		SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
		newParam.setType(ast.newPrimitiveType(PrimitiveType.INT));
		newParam.setName(ast.newSimpleName(name));
		return newParam;
	}

	private FieldDeclaration createNewField(ASTNode node, String name) {
		AST ast = node.getAST();
		VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
		frag.setName(ast.newSimpleName(name));
		FieldDeclaration newFieldDecl = ast.newFieldDeclaration(frag);
		newFieldDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		newFieldDecl.setType(ast.newPrimitiveType(PrimitiveType.INT));
		return newFieldDecl;
	}
	
	private FieldDeclaration getLastFieldDeclared(TypeDeclaration clazz) {
		int total = clazz.getFields().length - 1;
		return clazz.getFields()[total];
	}
}
