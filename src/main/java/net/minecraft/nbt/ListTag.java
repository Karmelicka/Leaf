package net.minecraft.nbt;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import me.titaniumtown.ArrayConstants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ListTag extends CollectionTag<Tag> {

    private static final int SELF_SIZE_IN_BYTES = 37;
    public static final TagType<ListTag> TYPE = new TagType.VariableSize<ListTag>() {
        @Override
        public ListTag load(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.pushDepth();

            ListTag nbttaglist;

            try {
                nbttaglist = loadList(input, tracker);
            } finally {
                tracker.popDepth();
            }

            return nbttaglist;
        }

        private static ListTag loadList(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(37L);
            byte b0 = input.readByte();
            int i = input.readInt();

            if (b0 == 0 && i > 0) {
                throw new NbtFormatException("Missing type on ListTag");
            } else {
                tracker.accountBytes(4L, (long) i);
                TagType<?> nbttagtype = TagTypes.getType(b0);
                List<Tag> list = Lists.newArrayListWithCapacity(i);

                for (int j = 0; j < i; ++j) {
                    list.add(nbttagtype.load(input, tracker));
                }

                return new ListTag(list, b0);
            }
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            tracker.pushDepth();

            StreamTagVisitor.ValueResult streamtagvisitor_b;

            try {
                streamtagvisitor_b = parseList(input, visitor, tracker);
            } finally {
                tracker.popDepth();
            }

            return streamtagvisitor_b;
        }

        private static StreamTagVisitor.ValueResult parseList(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(37L);
            TagType<?> nbttagtype = TagTypes.getType(input.readByte());
            int i = input.readInt();

            switch (visitor.visitList(nbttagtype, i)) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    nbttagtype.skip(input, i, tracker);
                    return visitor.visitContainerEnd();
                default:
                    tracker.accountBytes(4L, (long) i);
                    int j = 0;

                    while (true) {
                        if (j < i) {
                            label31:
                            {
                                switch (visitor.visitElement(nbttagtype, j)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                        nbttagtype.skip(input, tracker);
                                        break label31;
                                    case SKIP:
                                        nbttagtype.skip(input, tracker);
                                        break;
                                    default:
                                        switch (nbttagtype.parse(input, visitor, tracker)) {
                                            case HALT:
                                                return StreamTagVisitor.ValueResult.HALT;
                                            case BREAK:
                                                break label31;
                                        }
                                }

                                ++j;
                                continue;
                            }
                        }

                        int k = i - 1 - j;

                        if (k > 0) {
                            nbttagtype.skip(input, k, tracker);
                        }

                        return visitor.visitContainerEnd();
                    }
            }
        }

        @Override
        public void skip(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.pushDepth();

            try {
                TagType<?> nbttagtype = TagTypes.getType(input.readByte());
                int i = input.readInt();

                nbttagtype.skip(input, i, tracker);
            } finally {
                tracker.popDepth();
            }

        }

        @Override
        public String getName() {
            return "LIST";
        }

        @Override
        public String getPrettyName() {
            return "TAG_List";
        }
    };
    private final List<Tag> list;
    private byte type;

    public ListTag(List<Tag> list, byte type) { // PAIL: package-private -> public
        this.list = list;
        this.type = type;
    }

    public ListTag() {
        this(Lists.newArrayList(), (byte) 0);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        if (this.list.isEmpty()) {
            this.type = 0;
        } else {
            this.type = ((Tag) this.list.get(0)).getId();
        }

        output.writeByte(this.type);
        output.writeInt(this.list.size());
        Iterator iterator = this.list.iterator();

        while (iterator.hasNext()) {
            Tag nbtbase = (Tag) iterator.next();

            nbtbase.write(output);
        }

    }

    @Override
    public int sizeInBytes() {
        byte b0 = 37;
        int i = b0 + 4 * this.list.size();

        Tag nbtbase;

        for (Iterator iterator = this.list.iterator(); iterator.hasNext(); i += nbtbase.sizeInBytes()) {
            nbtbase = (Tag) iterator.next();
        }

        return i;
    }

    @Override
    public byte getId() {
        return 9;
    }

    @Override
    public TagType<ListTag> getType() {
        return ListTag.TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    private void updateTypeAfterRemove() {
        if (this.list.isEmpty()) {
            this.type = 0;
        }

    }

    @Override
    public Tag remove(int i) {
        Tag nbtbase = (Tag) this.list.remove(i);

        this.updateTypeAfterRemove();
        return nbtbase;
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public CompoundTag getCompound(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 10) {
                return (CompoundTag) nbtbase;
            }
        }

        return new CompoundTag();
    }

    public ListTag getList(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 9) {
                return (ListTag) nbtbase;
            }
        }

        return new ListTag();
    }

    public short getShort(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 2) {
                return ((ShortTag) nbtbase).getAsShort();
            }
        }

        return 0;
    }

    public int getInt(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 3) {
                return ((IntTag) nbtbase).getAsInt();
            }
        }

        return 0;
    }

    public int[] getIntArray(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 11) {
                return ((IntArrayTag) nbtbase).getAsIntArray();
            }
        }

        return ArrayConstants.emptyIntArray; // Gale - JettPack - reduce array allocations
    }

    public long[] getLongArray(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 12) {
                return ((LongArrayTag) nbtbase).getAsLongArray();
            }
        }

        return ArrayConstants.emptyLongArray; // Gale - JettPack - reduce array allocations
    }

    public double getDouble(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 6) {
                return ((DoubleTag) nbtbase).getAsDouble();
            }
        }

        return 0.0D;
    }

    public float getFloat(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            if (nbtbase.getId() == 5) {
                return ((FloatTag) nbtbase).getAsFloat();
            }
        }

        return 0.0F;
    }

    public String getString(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag nbtbase = (Tag) this.list.get(index);

            return nbtbase.getId() == 8 ? nbtbase.getAsString() : nbtbase.toString();
        } else {
            return "";
        }
    }

    public int size() {
        return this.list.size();
    }

    public Tag get(int i) {
        return (Tag) this.list.get(i);
    }

    @Override
    public Tag set(int i, Tag nbtbase) {
        Tag nbtbase1 = this.get(i);

        if (!this.setTag(i, nbtbase)) {
            throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to add tag of type %d to list of %d", nbtbase.getId(), this.type));
        } else {
            return nbtbase1;
        }
    }

    @Override
    public void add(int i, Tag nbtbase) {
        if (!this.addTag(i, nbtbase)) {
            throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to add tag of type %d to list of %d", nbtbase.getId(), this.type));
        }
    }

    @Override
    public boolean setTag(int index, Tag element) {
        if (this.updateType(element)) {
            this.list.set(index, element);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag element) {
        if (this.updateType(element)) {
            this.list.add(index, element);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateType(Tag element) {
        if (element.getId() == 0) {
            return false;
        } else if (this.type == 0) {
            this.type = element.getId();
            return true;
        } else {
            return this.type == element.getId();
        }
    }

    @Override
    public ListTag copy() {
        Iterable<Tag> iterable = TagTypes.getType(this.type).isValue() ? this.list : Iterables.transform(this.list, Tag::copy);
        List<Tag> list = Lists.newArrayList((Iterable) iterable);

        return new ListTag(list, this.type);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof ListTag && Objects.equals(this.list, ((ListTag) object).list);
    }

    public int hashCode() {
        return this.list.hashCode();
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitList(this);
    }

    @Override
    public byte getElementType() {
        return this.type;
    }

    public void clear() {
        this.list.clear();
        this.type = 0;
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        switch (visitor.visitList(TagTypes.getType(this.type), this.list.size())) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            default:
                int i = 0;

                while (i < this.list.size()) {
                    Tag nbtbase = (Tag) this.list.get(i);

                    switch (visitor.visitElement(nbtbase.getType(), i)) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        default:
                            switch (nbtbase.accept(visitor)) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                        case SKIP:
                            ++i;
                    }
                }

                return visitor.visitContainerEnd();
        }
    }
}
