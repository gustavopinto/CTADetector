package br.ufpe.cin.concurrency.forkjoinpatterns.util;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.CollectVariableInfo;

public class RefactoringUtil {
    private ASTRewrite rewriter;
    private CollectVariableInfo info;
    private IBinding fFieldBinding;
    
    public RefactoringUtil(ASTRewrite rw, CollectVariableInfo in, IBinding ib) {
        rewriter = rw;
        info = in;
        fFieldBinding = ib;
    }
    
    public void refactoringIfStmt(ASTNode ifStmt,
            MethodInvocation buggyInvoc, Expression condition, AST ast,
            boolean isGet) {
        ASTNode parent = ASTNodes.getParent(buggyInvoc, Statement.class);
        Block block = (Block) ASTNodes.getParent(ifStmt, Block.class);
        if (block != null) {
            if (parent instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement vDecl = (VariableDeclarationStatement) parent;
                String name = null;
                VariableDeclarationFragment removedFrag = null;
                for (Object o : vDecl.fragments()) {
                    VariableDeclarationFragment frag = (VariableDeclarationFragment) o;
                    Expression initializer = frag.getInitializer();
                    if (initializer instanceof MethodInvocation) {
                        MethodInvocation ini = (MethodInvocation) initializer;
                        if (ini.getExpression().toString()
                                .equals(fFieldBinding.getName())
                                && ini == buggyInvoc) {
                            name = frag.getName().getIdentifier();
                            removedFrag = frag;
                            break;
                        }
                    }
                }

                VariableDeclarationFragment newFrag = ast
                        .newVariableDeclarationFragment();
                newFrag.setName(ast.newSimpleName(name));
                VariableDeclarationStatement newVDecl = (VariableDeclarationStatement) ASTNode
                        .copySubtree(ast, vDecl);
                newVDecl.fragments().clear();
                newVDecl.fragments().add(newFrag);

                try {
                    rewriter.getListRewrite(block, block.STATEMENTS_PROPERTY)
                            .insertBefore(ifStmt, newVDecl, null);
                } catch (IllegalArgumentException e) {
                    rewriter.getListRewrite(block, block.STATEMENTS_PROPERTY)
                            .insertFirst(newVDecl, null);
                }
                if (vDecl.fragments().size() > 1)
                    rewriter.remove(removedFrag, null);
                else
                    rewriter.remove(vDecl, null);

                Assignment assign = ast.newAssignment();
                assign.setLeftHandSide(ast.newName(name));
                assign.setRightHandSide((MethodInvocation) ASTNode.copySubtree(
                        ast, buggyInvoc));
                ParenthesizedExpression pe = ast.newParenthesizedExpression();
                pe.setExpression(assign);
                if (isGet)
                    rewriter.replace(condition, pe, null);
                else {
                    InfixExpression isNullExpression = constructInfixExp(ast,
                            pe, ast.newNullLiteral(), Operator.NOT_EQUALS);
                    rewriter.replace(condition, isNullExpression, null);
                }
            } else if (parent instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) parent).getExpression();
                if (e instanceof Assignment) {
                    Expression left = ((Assignment) e).getLeftHandSide();
                    if (left instanceof Name) {
                        VariableDeclarationFragment frag = info
                                .getVariableMap().get(
                                        ((Name) left).resolveBinding());
                        if (ASTNodes.isParent(frag, ifStmt)) {
                            VariableDeclarationStatement decl = (VariableDeclarationStatement) ASTNodes
                                    .getParent(frag,
                                            VariableDeclarationStatement.class);
                            VariableDeclarationStatement newDecl = (VariableDeclarationStatement) ASTNode
                                    .copySubtree(ast, decl);
                            if (decl.fragments().size() > 1) {
                                VariableDeclarationFragment newFrag = (VariableDeclarationFragment) ASTNode
                                        .copySubtree(ast, frag);
                                newDecl.fragments().clear();
                                newDecl.fragments().add(newFrag);
                                rewriter.remove(frag, null);
                            } else
                                rewriter.remove(decl, null);
                            try {
                                rewriter.getListRewrite(block,
                                        block.STATEMENTS_PROPERTY)
                                        .insertBefore(ifStmt, newDecl, null);
                            } catch (IllegalArgumentException ie) {
                                rewriter.getListRewrite(block,
                                        block.STATEMENTS_PROPERTY).insertFirst(
                                        newDecl, null);
                            }
                        }
                        ParenthesizedExpression pe = ast
                                .newParenthesizedExpression();
                        pe.setExpression((Assignment) ASTNode.copySubtree(ast,
                                e));
                        if (isGet)
                            rewriter.replace(condition, pe, null);
                        else {
                            InfixExpression isNullExpression = constructInfixExp(
                                    ast, pe, ast.newNullLiteral(),
                                    Operator.NOT_EQUALS);
                            rewriter.replace(condition, isNullExpression, null);
                        }
                        rewriter.remove(parent, null);
                    }
                }
            }
        }
    }
    
    public static InfixExpression constructInfixExp(AST ast, Expression left,
            Expression right, Operator operator) {
        InfixExpression isNullExpression = ast.newInfixExpression();
        isNullExpression.setLeftOperand(left);
        isNullExpression.setOperator(operator);
        isNullExpression.setRightOperand(right);
        return isNullExpression;
    }
}
