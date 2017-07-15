/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.parser.parser;

import org.truffleruby.parser.ast.ParseNode;
import org.truffleruby.parser.ast.PreExeParseNode;
import org.truffleruby.parser.scope.DynamicScope;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class RubyParserResult {
    final public static List<ParseNode> EMPTY_BEGIN_LIST = new ArrayList<>();

    private List<ParseNode> beginNodes;
    private ParseNode ast;
    private DynamicScope scope;
    
    public ParseNode getAST() {
        return ast;
    }
    
    public DynamicScope getScope() {
        return scope;
    }
    
    public void setScope(DynamicScope scope) {
        this.scope = scope;
    }

    /**
     * Sets the ast.
     * @param ast The ast to set
     */
    public void setAST(ParseNode ast) {
        this.ast = ast;
    }
    
    public void addBeginNode(PreExeParseNode node) {
        if (beginNodes == null) beginNodes = new ArrayList<>();
    	beginNodes.add(node);
    }
    
    public List<ParseNode> getBeginNodes() {
        return beginNodes == null ? EMPTY_BEGIN_LIST : beginNodes;
    }

}
