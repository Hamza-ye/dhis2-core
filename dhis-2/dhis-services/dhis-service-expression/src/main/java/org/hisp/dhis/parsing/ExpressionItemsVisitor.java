package org.hisp.dhis.parsing;

/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.antlr.v4.runtime.tree.ParseTree;
import org.hisp.dhis.analytics.AggregationType;
import org.hisp.dhis.common.DimensionItemType;
import org.hisp.dhis.common.DimensionService;
import org.hisp.dhis.common.DimensionalItemObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.period.Period;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hisp.dhis.common.DimensionItemType.*;
import static org.hisp.dhis.parsing.generated.ExpressionParser.*;

/**
 * ANTLR parse tree visitor to find the expression items in an expression.
 * <p/>
 * Uses the ANTLR visitor partern.
 *
 * @author Jim Grace
 */
public class ExpressionItemsVisitor extends ExpressionVisitor
{
    @Autowired
    private DimensionService dimensionService;

    /**
     * Dummy value to use when value is not yet known.
     */
    private final static Double DUMMY_VALUE = Double.valueOf( 1. );

    private Set<ExpressionItem> itemsNeeded;

    public void getDimensionalItemObjects( ParseTree parseTree, List<OrganisationUnit> orgUnits,
        List<Period> periods, Map<String, Double> constantMap, Set<ExpressionItem> itemsNeeded,
        OrganisationUnitService _organisationUnitService, IdentifiableObjectManager _manager,
        DimensionService _dimensionService )
    {
        organisationUnitService = _organisationUnitService;
        manager = _manager;
        dimensionService = _dimensionService;

        this.constantMap = constantMap;
        this.itemsNeeded = itemsNeeded;

        for ( OrganisationUnit orgUnit : orgUnits )
        {
            currentOrgUnit = orgUnit;

            for ( Period period : periods )
            {
                currentPeriod = period ;

                castDouble( visit( parseTree ) );
            }
        }
    }

    public String getExpressionDescription( ParseTree parseTree, String expr )
    {
        itemsNeeded = new HashSet<ExpressionItem>();

        castDouble( visit( parseTree ) );

        Map<String, String> nameMap = new HashMap<>();

        for ( ExpressionItem item : itemsNeeded )
        {
            DimensionalItemObject itemObject = item.getDimensionalItemObject();

            nameMap.put( itemObject.getDimensionItem(), itemObject.getName() );
        }

        String description = expr;

        for ( Map.Entry<String, String> entry : nameMap.entrySet() )
        {
            description.replace( entry.getKey(), entry.getValue() );
        }

        return description;
    }

    // -------------------------------------------------------------------------
    // Visitor methods implemented here
    // -------------------------------------------------------------------------

    @Override
    public Object visitDataElement ( DataElementContext ctx )
    {
        return getExpressionItem( DATA_ELEMENT, castString( ctx.dataElementId().getText() ) );
    }

    @Override
    public Object visitDataElementOperand ( DataElementOperandContext ctx )
    {
        return getExpressionItem( DATA_ELEMENT_OPERAND, castString( ctx.dataElementOperandId().getText() ) );
    }

    @Override
    public Object visitProgramDataElement ( ProgramDataElementContext ctx )
    {
        return getExpressionItem( PROGRAM_DATA_ELEMENT, castString( ctx.programDataElementId().getText() ) );
    }

    @Override
    public Object visitProgramTrackedEntityAttribute ( ProgramTrackedEntityAttributeContext ctx )
    {
        return getExpressionItem( PROGRAM_ATTRIBUTE, castString( ctx.programTrackedEntityAttributeId().getText() ) );
    }

    @Override
    public Object visitProgramIndicator ( ProgramIndicatorContext ctx )
    {
        return getExpressionItem( PROGRAM_INDICATOR, castString( ctx.programIndicatorId().getText() ) );
    }

//    @Override
//    public Object visitDimensionItemObject( DimensionItemObjectContext ctx )
//    {
//        DimensionalItemObject item = dimensionService.getDataDimensionalItemObject( ctx.getText() );
//
//        if ( item == null )
//        {
//            throw new ParsingException( "Can't find object matching '" + ctx.getText() + "'" );
//        }
//
//        itemsNeeded.putValue( currentOrgUnit, currentPeriod, item );
//
//        return DUMMY_VALUE;
//    };

    @Override
    public final Object visitOrgUnitCount( OrgUnitCountContext ctx )
    {
        return DUMMY_VALUE;
    };

    @Override
    public final Object visitDays( DaysContext ctx )
    {
        return DUMMY_VALUE;
    };

    // -------------------------------------------------------------------------
    // Logical methods implemented here
    // -------------------------------------------------------------------------

    @Override
    protected final Object functionAnd( ExprContext ctx )
    {
        // Always visit both args.

        Boolean leftBool = castBoolean( visit( ctx.expr( 0 ) ) );
        Boolean rightBool = castBoolean( visit( ctx.expr( 1 ) ) );

        return leftBool && rightBool;
    }

    @Override
    protected final Object functionOr( ExprContext ctx )
    {
        // Always visit both args.

        Boolean leftBool = castBoolean( visit( ctx.expr( 0 ) ) );
        Boolean rightBool = castBoolean( visit( ctx.expr( 1 ) ) );

        return leftBool || rightBool;
    }

    @Override
    protected final Object functionIf( ExprContext ctx )
    {
        // Always visit both results.

        Boolean test = castBoolean( visit( ctx.a3().expr( 0 ) ) );
        Object ifTrue = visit( ctx.a3().expr( 1 ) );
        Object ifFalse = visit( ctx.a3().expr( 2 ) );

        return test ? ifTrue : ifFalse;
    }

    @Override
    protected final Object functionCoalesce( ExprContext ctx )
    {
        // Always visit all args.

        Object returnVal = null;

        for ( ExprContext c : ctx.a1_n().expr() )
        {
            Object val = visit( c );
            if ( returnVal == null && val != null )
            {
                returnVal = val;
            }
        }
        return returnVal;
    }

    @Override
    protected final Object functionExcept( ExprContext ctx )
    {
        // Visit the test and type-check it.
        // Always visit the expression.

        castBoolean( visit( ctx.a1().expr() ) );

        return visit( ctx.expr( 0 ) );
    }

    // -------------------------------------------------------------------------
    // Supportive methods
    // -------------------------------------------------------------------------

    private Object getExpressionItem( DimensionItemType type, String itemId )
    {
        DimensionalItemObject item = dimensionService.getDataDimensionalItemObject( itemId );

        if ( item == null )
        {
            throw new ParsingException( "Can't find " + type.name() + " matching '" + itemId + "'" );
        }

        if ( item.getDimensionItemType() != type )
        {
            throw new ParsingException( "Expected " + type.name() + " but found " + item.getDimensionItemType().name() + " " + itemId );
        }

        AggregationType aggregationType = currentAggregationType != null ? currentAggregationType : item.getAggregationType();

        itemsNeeded.add( new ExpressionItem( currentOrgUnit, currentPeriod, item, aggregationType ) );

        return DUMMY_VALUE;
    }
}
