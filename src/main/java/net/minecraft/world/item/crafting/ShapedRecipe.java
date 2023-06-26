package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.inventory.RecipeChoice;
// CraftBukkit end

public class ShapedRecipe extends io.papermc.paper.inventory.recipe.RecipeBookExactChoiceRecipe<CraftingContainer> implements CraftingRecipe { // Paper - improve exact recipe choices

    final ShapedRecipePattern pattern;
    final ItemStack result;
    final String group;
    final CraftingBookCategory category;
    final boolean showNotification;

    public ShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern raw, ItemStack result, boolean showNotification) {
        this.group = group;
        this.category = category;
        this.pattern = raw;
        this.result = result;
        this.showNotification = showNotification;
        this.checkExactIngredients(); // Paper - improve exact recipe choices
    }

    public ShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern raw, ItemStack result) {
        this(group, category, raw, result, true);
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapedRecipe recipe = new CraftShapedRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        switch (this.pattern.height()) {
        case 1:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a");
                break;
            case 2:
                recipe.shape("ab");
                break;
            case 3:
                recipe.shape("abc");
                break;
            }
            break;
        case 2:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a","b");
                break;
            case 2:
                recipe.shape("ab","cd");
                break;
            case 3:
                recipe.shape("abc","def");
                break;
            }
            break;
        case 3:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a","b","c");
                break;
            case 2:
                recipe.shape("ab","cd","ef");
                break;
            case 3:
                recipe.shape("abc","def","ghi");
                break;
            }
            break;
        }
        char c = 'a';
        for (Ingredient list : this.pattern.ingredients()) {
            RecipeChoice choice = CraftRecipe.toBukkit(list);
            if (choice != null) {
                recipe.setIngredient(c, choice);
            }

            c++;
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.pattern.width() && height >= this.pattern.height();
    }

    public boolean matches(CraftingContainer inventory, Level world) {
        return this.pattern.matches(inventory);
    }

    public ItemStack assemble(CraftingContainer inventory, RegistryAccess registryManager) {
        return this.getResultItem(registryManager).copy();
    }

    public int getWidth() {
        return this.pattern.width();
    }

    public int getHeight() {
        return this.pattern.height();
    }

    @Override
    public boolean isIncomplete() {
        NonNullList<Ingredient> nonnulllist = this.getIngredients();

        return nonnulllist.isEmpty() || nonnulllist.stream().filter((recipeitemstack) -> {
            return !recipeitemstack.isEmpty();
        }).anyMatch((recipeitemstack) -> {
            return recipeitemstack.getItems().length == 0;
        });
    }

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {

        public static final Codec<ShapedRecipe> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ExtraCodecs.strictOptionalField(Codec.STRING, "group", "").forGetter((shapedrecipes) -> {
                return shapedrecipes.group;
            }), CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter((shapedrecipes) -> {
                return shapedrecipes.category;
            }), ShapedRecipePattern.MAP_CODEC.forGetter((shapedrecipes) -> {
                return shapedrecipes.pattern;
            }), ItemStack.ITEM_WITH_COUNT_CODEC.fieldOf("result").forGetter((shapedrecipes) -> {
                return shapedrecipes.result;
            }), ExtraCodecs.strictOptionalField(Codec.BOOL, "show_notification", true).forGetter((shapedrecipes) -> {
                return shapedrecipes.showNotification;
            })).apply(instance, ShapedRecipe::new);
        });

        public Serializer() {}

        @Override
        public Codec<ShapedRecipe> codec() {
            return ShapedRecipe.Serializer.CODEC;
        }

        @Override
        public ShapedRecipe fromNetwork(FriendlyByteBuf buf) {
            String s = buf.readUtf();
            CraftingBookCategory craftingbookcategory = (CraftingBookCategory) buf.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern shapedrecipepattern = ShapedRecipePattern.fromNetwork(buf);
            ItemStack itemstack = buf.readItem();
            boolean flag = buf.readBoolean();

            return new ShapedRecipe(s, craftingbookcategory, shapedrecipepattern, itemstack, flag);
        }

        public void toNetwork(FriendlyByteBuf buf, ShapedRecipe recipe) {
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            recipe.pattern.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeBoolean(recipe.showNotification);
        }
    }
}
