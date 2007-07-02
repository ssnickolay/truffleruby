/*
 * DefnNodeCompiler.java
 *
 * Created on January 4, 2007, 2:07 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.jruby.compiler;

import org.jruby.ast.ArgsNode;
import org.jruby.ast.DefnNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.Node;
import org.jruby.runtime.Arity;

/**
 *
 * @author headius
 */
public class DefnNodeCompiler implements NodeCompiler {
    
    /** Creates a new instance of DefnNodeCompiler */
    public DefnNodeCompiler() {
        
    }
    
    public void compile(Node node, Compiler context) {
        context.lineNumber(node.getPosition());
        
        final DefnNode defnNode = (DefnNode)node;
        final ArgsNode argsNode = defnNode.getArgsNode();
        
        NodeCompilerFactory.confirmNodeIsSafe(argsNode);
        
        ClosureCallback body = new ClosureCallback() {
            public void compile(Compiler context) {
                if (defnNode.getBodyNode() != null) {
                    NodeCompilerFactory.getCompiler(defnNode.getBodyNode()).compile(defnNode.getBodyNode(), context);
                } else {
                    context.loadNil();
                }
            }
        };
        
        final ArrayCallback evalOptionalValue = new ArrayCallback() {
            public void nextValue(Compiler context, Object object, int index) {
                ListNode optArgs = (ListNode)object;
                
                Node node = optArgs.get(index);

                NodeCompilerFactory.getCompiler(node).compile(node, context);
            }
        };
        
        ClosureCallback args = new ClosureCallback() {
            public void compile(Compiler context) {
                int required = argsNode.getArgsCount();
                int restArg = argsNode.getRestArg();
                boolean hasOptArgs = argsNode.getOptArgs() != null;
                Arity arity = argsNode.getArity();
                
                context.lineNumber(argsNode.getPosition());
                
                if (hasOptArgs) {
                    if (restArg > -1) {
                        int opt = argsNode.getOptArgs().size();
                        context.processRequiredArgs(arity, required, opt, restArg);
                        
                        ListNode optArgs = argsNode.getOptArgs();
                        context.assignOptionalArgs(optArgs, required, opt, evalOptionalValue);
                        
                        context.processRestArg(required + opt, restArg);
                    } else {
                        int opt = argsNode.getOptArgs().size();
                        context.processRequiredArgs(arity, required, opt, restArg);
                        
                        ListNode optArgs = argsNode.getOptArgs();
                        context.assignOptionalArgs(optArgs, required, opt, evalOptionalValue);
                    }
                } else {
                    if (restArg > -1) {
                        context.processRequiredArgs(arity, required, 0, restArg);
                        
                        context.processRestArg(required, restArg);
                    } else {
                        context.processRequiredArgs(arity, required, 0, restArg);
                    }
                }
            }
        };
        
        context.defineNewMethod(defnNode.getName(), defnNode.getScope(), body, args);
    }
    
}
