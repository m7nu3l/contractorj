package j2bpl;

import soot.Unit;
import soot.toolkits.graph.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BasicBlock {

    private final LocalMethod method;

    private final Block block;

    private static Map<Block, BasicBlock> blockToBasicBlock = new HashMap<>();

    public static BasicBlock create(LocalMethod method, Block block) {

        if (blockToBasicBlock.containsKey(block)) {
            return blockToBasicBlock.get(block);
        }

        final BasicBlock basicBlock = new BasicBlock(method, block);
        blockToBasicBlock.put(block, basicBlock);

        return basicBlock;
    }

    private BasicBlock(LocalMethod method, Block block) {

        this.method = method;
        this.block = block;
    }

    public String getLabel() {

        final String methodName = method.getTranslatedName();
        return methodName + "_" + block.getIndexInMethod();
    }

    public BasicBlock getSuccessorBasicBlock(Unit headOfSuccessor) {

        for (final Block successorBlock : block.getSuccs()) {

            if (successorBlock.getHead() == headOfSuccessor) {
                return create(method, successorBlock);
            }
        }

        throw new IllegalArgumentException("headOfSuccessor not found in any successor block");
    }

    public List<String> getTranslatedInstructions() {

        final ArrayList<String> translations = new ArrayList<>();

        translations.add(getLabel() + ":");

        final Iterator<Unit> unitIterator = block.iterator();
        while (unitIterator.hasNext()) {

            final Unit unit = unitIterator.next();
            final UnitTranslator unitTranslator = new UnitTranslator(method, this);

            unit.apply(unitTranslator);
            final List<String> translation = unitTranslator.getTranslation();

            for (String statement : translation) {
                translations.add(StringUtils.indent(statement));
            }
        }

        return translations;
    }
}