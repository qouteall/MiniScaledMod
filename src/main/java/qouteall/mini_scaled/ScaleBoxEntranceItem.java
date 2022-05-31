package qouteall.mini_scaled;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ScaleBoxEntranceItem extends Item {
    
    public static final ScaleBoxEntranceItem instance = new ScaleBoxEntranceItem(new Item.Settings().group(ItemGroup.MISC));
    
    public static void init() {
        Registry.register(
            Registry.ITEM,
            new Identifier("mini_scaled:scale_box_item"),
            instance
        );
    }
    
    public static class ItemInfo {
        public int scale;
        public DyeColor color;
        @Nullable
        public UUID ownerId;
        @Nullable
        public String ownerNameCache;
        
        public ItemInfo(int scale, DyeColor color) {
            this.scale = scale;
            this.color = color;
        }
        
        public ItemInfo(int size, DyeColor color, @NotNull UUID ownerId, @NotNull String ownerNameCache) {
            this.scale = size;
            this.color = color;
            this.ownerId = ownerId;
            this.ownerNameCache = ownerNameCache;
        }
        
        public ItemInfo(NbtCompound tag) {
            scale = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            if (tag.contains("ownerId")) {
                ownerId = tag.getUuid("ownerId");
                ownerNameCache = tag.getString("ownerNameCache");
            }
        }
        
        public void writeToTag(NbtCompound compoundTag) {
            compoundTag.putInt("size", scale);
            compoundTag.putString("color", color.getName());
            if (ownerId != null) {
                compoundTag.putUuid("ownerId", ownerId);
                compoundTag.putString("ownerNameCache", ownerNameCache);
            }
        }
    }
    
    public ScaleBoxEntranceItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        World world = context.getWorld();
        
        if (world.isClient()) {
            return ActionResult.FAIL;
        }
        
        if (context.getPlayer() == null) {
            return ActionResult.FAIL;
        }
        
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        
        if (!world.isAir(pos)) {
            return ActionResult.FAIL;
        }
        
        ItemStack stack = context.getStack();
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateNbt());
        
        ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
        
        int size = itemInfo.scale;
        if (!ScaleBoxGeneration.isValidScale(size)) {
            player.sendMessage(new LiteralText("bad item data"), false);
            return ActionResult.FAIL;
        }
        
        UUID ownerId = itemInfo.ownerId;
        String ownerNameCache = itemInfo.ownerNameCache;
        
        if (ownerId == null) {
            ownerId = player.getUuid();
        }
        if (ownerNameCache == null) {
            ownerNameCache = player.getName().asString();
        }
        
        ScaleBoxGeneration.putScaleBox(
            ((ServerWorld) world),
            ownerId, ownerNameCache,
            size,
            pos,
            itemInfo.color
        );
        
        stack.decrement(1);
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateNbt());
        tooltip.add(new TranslatableText("mini_scaled.color")
            .append(getColorText(itemInfo.color).formatted(Formatting.GOLD))
        );
        tooltip.add(new TranslatableText("mini_scaled.size")
            .append(new LiteralText(Integer.toString(itemInfo.scale)).formatted(Formatting.AQUA))
        );
        if (itemInfo.ownerNameCache != null) {
            tooltip.add(new TranslatableText("mini_scaled.owner")
                .append(new LiteralText(itemInfo.ownerNameCache).formatted(Formatting.YELLOW))
            );
        }
    }
    
    @Override
    public void appendStacks(ItemGroup group, DefaultedList<ItemStack> stacks) {
        if (this.isIn(group)) {
            for (int scale : ScaleBoxGeneration.supportedScales) {
                for (DyeColor dyeColor : DyeColor.values()) {
                    ItemStack itemStack = new ItemStack(instance);
                    
                    ItemInfo itemInfo = new ItemInfo(scale, dyeColor);
                    itemInfo.writeToTag(itemStack.getOrCreateNbt());
                    
                    stacks.add(itemStack);
                }
            }
        }
    }
    
    private static final Text spaceText = new LiteralText(" ");
    
    @Override
    public Text getName(ItemStack stack) {
        ItemInfo itemInfo = new ItemInfo(stack.getOrCreateNbt());
        DyeColor color = itemInfo.color;
        MutableText result = new TranslatableText("item.mini_scaled.scale_box_item")
            .append(spaceText)
            .append(new LiteralText(Integer.toString(itemInfo.scale)))
            .append(spaceText)
            .append(getColorText(color));
        if (itemInfo.ownerNameCache != null) {
            result = result.append(spaceText)
                .append(new TranslatableText("mini_scaled.owner"))
                .append(new LiteralText(itemInfo.ownerNameCache));
        }
        return result;
    }
    
    public static TranslatableText getColorText(DyeColor color) {
        return new TranslatableText("color.minecraft." + color.getName());
    }
    
    // nullable
    public static ItemStack boxIdToItem(int boxId) {
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry == null) {
            System.err.println("invalid boxId for item " + boxId);
            return null;
        }
        
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        new ScaleBoxEntranceItem.ItemInfo(
            entry.scale, entry.color, entry.ownerId, entry.ownerNameCache
        ).writeToTag(itemStack.getOrCreateNbt());
        
        return itemStack;
    }
    
    public static int getRenderingColor(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return 0;
        }
        // does not use ItemInfo to improve performance
        String colorText = nbt.getString("color");
        DyeColor dyeColor = DyeColor.byName(colorText, DyeColor.BLACK);
        return dyeColor.getMapColor().color;
    }
}
