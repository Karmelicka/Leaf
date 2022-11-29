package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ExtraCodecs {
    public static final Codec<JsonElement> JSON = converter(JsonOps.INSTANCE);
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    public static final Codec<JsonElement> FLAT_JSON = Codec.STRING.flatXmap((json) -> {
        try {
            return DataResult.success(JsonParser.parseString(json));
        } catch (JsonParseException var2) {
            return DataResult.error(var2::getMessage);
        }
    }, (json) -> {
        try {
            return DataResult.success(GsonHelper.toStableString(json));
        } catch (IllegalArgumentException var2) {
            return DataResult.error(var2::getMessage);
        }
    });
    public static final Codec<Vector3f> VECTOR3F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 3).map((listx) -> {
            return new Vector3f(listx.get(0), listx.get(1), listx.get(2));
        });
    }, (vec3f) -> {
        return List.of(vec3f.x(), vec3f.y(), vec3f.z());
    });
    public static final Codec<Quaternionf> QUATERNIONF_COMPONENTS = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 4).map((listx) -> {
            return new Quaternionf(listx.get(0), listx.get(1), listx.get(2), listx.get(3));
        });
    }, (quaternion) -> {
        return List.of(quaternion.x, quaternion.y, quaternion.z, quaternion.w);
    });
    public static final Codec<AxisAngle4f> AXISANGLE4F = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("angle").forGetter((axisAngle) -> {
            return axisAngle.angle;
        }), VECTOR3F.fieldOf("axis").forGetter((axisAngle) -> {
            return new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z);
        })).apply(instance, AxisAngle4f::new);
    });
    public static final Codec<Quaternionf> QUATERNIONF = withAlternative(QUATERNIONF_COMPONENTS, AXISANGLE4F.xmap(Quaternionf::new, AxisAngle4f::new));
    public static Codec<Matrix4f> MATRIX4F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 16).map((listx) -> {
            Matrix4f matrix4f = new Matrix4f();

            for(int i = 0; i < listx.size(); ++i) {
                matrix4f.setRowColumn(i >> 2, i & 3, listx.get(i));
            }

            return matrix4f.determineProperties();
        });
    }, (matrix4f) -> {
        FloatList floatList = new FloatArrayList(16);

        for(int i = 0; i < 16; ++i) {
            floatList.add(matrix4f.getRowColumn(i >> 2, i & 3));
        }

        return floatList;
    });
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, (v) -> {
        return "Value must be non-negative: " + v;
    });
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, (v) -> {
        return "Value must be positive: " + v;
    });
    public static final Codec<Float> POSITIVE_FLOAT = floatRangeMinExclusiveWithMessage(0.0F, Float.MAX_VALUE, (v) -> {
        return "Value must be positive: " + v;
    });
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap((pattern) -> {
        try {
            return DataResult.success(Pattern.compile(pattern));
        } catch (PatternSyntaxException var2) {
            return DataResult.error(() -> {
                return "Invalid regex pattern '" + pattern + "': " + var2.getMessage();
            });
        }
    }, Pattern::pattern);
    public static final Codec<Instant> INSTANT_ISO8601 = temporalCodec(DateTimeFormatter.ISO_INSTANT).xmap(Instant::from, Function.identity());
    public static final Codec<byte[]> BASE64_STRING = Codec.STRING.comapFlatMap((encoded) -> {
        try {
            return DataResult.success(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> {
                return "Malformed base64 string";
            });
        }
    }, (data) -> {
        return Base64.getEncoder().encodeToString(data);
    });
    public static final Codec<String> ESCAPED_STRING = Codec.STRING.comapFlatMap((string) -> {
        return DataResult.success(StringEscapeUtils.unescapeJava(string));
    }, StringEscapeUtils::escapeJava);
    public static final Codec<ExtraCodecs.TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING.comapFlatMap((tagEntry) -> {
        return tagEntry.startsWith("#") ? ResourceLocation.read(tagEntry.substring(1)).map((id) -> {
            return new ExtraCodecs.TagOrElementLocation(id, true);
        }) : ResourceLocation.read(tagEntry).map((id) -> {
            return new ExtraCodecs.TagOrElementLocation(id, false);
        });
    }, ExtraCodecs.TagOrElementLocation::decoratedId);
    public static final Function<Optional<Long>, OptionalLong> toOptionalLong = (optional) -> {
        return optional.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    };
    public static final Function<OptionalLong, Optional<Long>> fromOptionalLong = (optionalLong) -> {
        return optionalLong.isPresent() ? Optional.of(optionalLong.getAsLong()) : Optional.empty();
    };
    public static final Codec<BitSet> BIT_SET = Codec.LONG_STREAM.xmap((stream) -> {
        return BitSet.valueOf(stream.toArray());
    }, (set) -> {
        return Arrays.stream(set.toLongArray());
    });
    private static final Codec<Property> PROPERTY = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.STRING.fieldOf("name").forGetter(Property::name), Codec.STRING.fieldOf("value").forGetter(Property::value), Codec.STRING.optionalFieldOf("signature").forGetter((property) -> {
            return Optional.ofNullable(property.signature());
        })).apply(instance, (key, value, signature) -> {
            return new Property(key, value, signature.orElse((String)null));
        });
    });
    @VisibleForTesting
    public static final Codec<PropertyMap> PROPERTY_MAP = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()), PROPERTY.listOf()).xmap((either) -> {
        PropertyMap propertyMap = new PropertyMap();
        either.ifLeft((map) -> {
            map.forEach((key, values) -> {
                for(String string : values) {
                    propertyMap.put(key, new Property(key, string));
                }

            });
        }).ifRight((properties) -> {
            for(Property property : properties) {
                propertyMap.put(property.name(), property);
            }

        });
        return propertyMap;
    }, (properties) -> {
        return Either.right(properties.values().stream().toList());
    });
    private static final MapCodec<GameProfile> GAME_PROFILE_WITHOUT_PROPERTIES = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(UUIDUtil.AUTHLIB_CODEC.fieldOf("id").forGetter(GameProfile::getId), Codec.STRING.fieldOf("name").forGetter(GameProfile::getName)).apply(instance, GameProfile::new);
    });
    public static final Codec<GameProfile> GAME_PROFILE = RecordCodecBuilder.create((instance) -> {
        return instance.group(GAME_PROFILE_WITHOUT_PROPERTIES.forGetter(Function.identity()), PROPERTY_MAP.optionalFieldOf("properties", new PropertyMap()).forGetter(GameProfile::getProperties)).apply(instance, (profile, properties) -> {
            properties.forEach((key, property) -> {
                profile.getProperties().put(key, property);
            });
            return profile;
        });
    });
    public static final Codec<String> NON_EMPTY_STRING = validate(Codec.STRING, (string) -> {
        return string.isEmpty() ? DataResult.error(() -> {
            return "Expected non-empty string";
        }) : DataResult.success(string);
    });
    public static final Codec<Integer> CODEPOINT = Codec.STRING.comapFlatMap((string) -> {
        int[] is = string.codePoints().toArray();
        return is.length != 1 ? DataResult.error(() -> {
            return "Expected one codepoint, got: " + string;
        }) : DataResult.success(is[0]);
    }, Character::toString);
    public static Codec<String> RESOURCE_PATH_CODEC = validate(Codec.STRING, (path) -> {
        return !ResourceLocation.isValidResourceLocation(path) ? DataResult.error(() -> { // Gale - dev import deobfuscation fixes
            return "Invalid string to use as a resource path element: " + path;
        }) : DataResult.success(path);
    });

    public static <T> Codec<T> converter(DynamicOps<T> ops) {
        return Codec.PASSTHROUGH.xmap((dynamic) -> {
            return dynamic.convert(ops).getValue();
        }, (object) -> {
            return new Dynamic<>(ops, object);
        });
    }

    public static <F, S> Codec<Either<F, S>> xor(Codec<F> first, Codec<S> second) {
        return new ExtraCodecs.XorCodec<>(first, second);
    }

    public static <P, I> Codec<I> intervalCodec(Codec<P> codec, String leftFieldName, String rightFieldName, BiFunction<P, P, DataResult<I>> combineFunction, Function<I, P> leftFunction, Function<I, P> rightFunction) {
        Codec<I> codec2 = Codec.list(codec).comapFlatMap((list) -> {
            return Util.fixedSize(list, 2).flatMap((listx) -> {
                P object = listx.get(0);
                P object2 = listx.get(1);
                return combineFunction.apply(object, object2);
            });
        }, (pair) -> {
            return ImmutableList.of(leftFunction.apply(pair), rightFunction.apply(pair));
        });
        Codec<I> codec3 = RecordCodecBuilder.<Pair>create((instance) -> {
            return instance.group(codec.fieldOf(leftFieldName).forGetter(pair -> (P) pair.getFirst()), codec.fieldOf(rightFieldName).forGetter(pair -> (P) pair.getSecond())).apply(instance, Pair::of); // Gale - dev import deobfuscation fixes
        }).comapFlatMap((pair) -> {
            return combineFunction.apply((P)pair.getFirst(), (P)pair.getSecond());
        }, (pair) -> {
            return Pair.of(leftFunction.apply(pair), rightFunction.apply(pair));
        });
        Codec<I> codec4 = withAlternative(codec2, codec3);
        return Codec.either(codec, codec4).comapFlatMap((either) -> {
            return either.map((object) -> {
                return combineFunction.apply(object, object);
            }, DataResult::success);
        }, (pair) -> {
            P object = leftFunction.apply(pair);
            P object2 = rightFunction.apply(pair);
            return Objects.equals(object, object2) ? Either.left(object) : Either.right(pair);
        });
    }

    public static <A> Codec.ResultFunction<A> orElsePartial(final A object) {
        return new Codec.ResultFunction<A>() {
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> dynamicOps, T objectx, DataResult<Pair<A, T>> dataResult) {
                MutableObject<String> mutableObject = new MutableObject<>();
                Optional<Pair<A, T>> optional = dataResult.resultOrPartial(mutableObject::setValue);
                return optional.isPresent() ? dataResult : DataResult.error(() -> {
                    return "(" + (String)mutableObject.getValue() + " -> using default)";
                }, Pair.of(object, (T) object)); // Gale - dev import deobfuscation fixes
            }

            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, A objectx, DataResult<T> dataResult) {
                return dataResult;
            }

            @Override
            public String toString() {
                return "OrElsePartial[" + object + "]";
            }
        };
    }

    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> elementToRawId, IntFunction<E> rawIdToElement, int errorRawId) {
        return Codec.INT.flatXmap((rawId) -> {
            return Optional.ofNullable(rawIdToElement.apply(rawId)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Unknown element id: " + rawId;
                });
            });
        }, (element) -> {
            int j = elementToRawId.applyAsInt(element);
            return j == errorRawId ? DataResult.error(() -> {
                return "Element with unknown id: " + element;
            }) : DataResult.success(j);
        });
    }

    public static <E> Codec<E> stringResolverCodec(Function<E, String> elementToId, Function<String, E> idToElement) {
        return Codec.STRING.flatXmap((id) -> {
            return Optional.ofNullable(idToElement.apply(id)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Unknown element name:" + id;
                });
            });
        }, (element) -> {
            return Optional.ofNullable(elementToId.apply(element)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Element with unknown name: " + element;
                });
            });
        });
    }

    public static <E> Codec<E> orCompressed(final Codec<E> uncompressedCodec, final Codec<E> compressedCodec) {
        return new Codec<E>() {
            public <T> DataResult<T> encode(E object, DynamicOps<T> dynamicOps, T object2) {
                return dynamicOps.compressMaps() ? compressedCodec.encode(object, dynamicOps, object2) : uncompressedCodec.encode(object, dynamicOps, object2);
            }

            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> dynamicOps, T object) {
                return dynamicOps.compressMaps() ? compressedCodec.decode(dynamicOps, object) : uncompressedCodec.decode(dynamicOps, object);
            }

            @Override
            public String toString() {
                return uncompressedCodec + " orCompressed " + compressedCodec;
            }
        };
    }

    public static <E> MapCodec<E> orCompressed(final MapCodec<E> uncompressedCodec, final MapCodec<E> compressedCodec) {
        return new MapCodec<E>() {
            public <T> RecordBuilder<T> encode(E object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                return dynamicOps.compressMaps() ? compressedCodec.encode(object, dynamicOps, recordBuilder) : uncompressedCodec.encode(object, dynamicOps, recordBuilder);
            }

            public <T> DataResult<E> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                return dynamicOps.compressMaps() ? compressedCodec.decode(dynamicOps, mapLike) : uncompressedCodec.decode(dynamicOps, mapLike);
            }

            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return compressedCodec.keys(dynamicOps);
            }

            public String toString() {
                return uncompressedCodec + " orCompressed " + compressedCodec;
            }
        };
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> originalCodec, final Function<E, Lifecycle> entryLifecycleGetter, final Function<E, Lifecycle> lifecycleGetter) {
        return originalCodec.mapResult(new Codec.ResultFunction<E>() {
            public <T> DataResult<Pair<E, T>> apply(DynamicOps<T> dynamicOps, T object, DataResult<Pair<E, T>> dataResult) {
                return dataResult.result().map((pair) -> {
                    return dataResult.setLifecycle(entryLifecycleGetter.apply(pair.getFirst()));
                }).orElse(dataResult);
            }

            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, E object, DataResult<T> dataResult) {
                return dataResult.setLifecycle(lifecycleGetter.apply(object));
            }

            @Override
            public String toString() {
                return "WithLifecycle[" + entryLifecycleGetter + " " + lifecycleGetter + "]";
            }
        });
    }

    public static <F, S> ExtraCodecs.EitherCodec<F, S> either(Codec<F> first, Codec<S> second) {
        return new ExtraCodecs.EitherCodec<>(first, second);
    }

    public static <K, V> ExtraCodecs.StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> keyCodec, Codec<V> elementCodec) {
        return new ExtraCodecs.StrictUnboundedMapCodec<>(keyCodec, elementCodec);
    }

    public static <T> Codec<T> validate(Codec<T> codec, Function<T, DataResult<T>> validator) {
        if (codec instanceof MapCodec.MapCodecCodec<T> mapCodecCodec) {
            return validate(mapCodecCodec.codec(), validator).codec();
        } else {
            return codec.flatXmap(validator, validator);
        }
    }

    public static <T> MapCodec<T> validate(MapCodec<T> codec, Function<T, DataResult<T>> validator) {
        return codec.flatXmap(validator, validator);
    }

    private static Codec<Integer> intRangeWithMessage(int min, int max, Function<Integer, String> messageFactory) {
        return validate(Codec.INT, (value) -> {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0 ? DataResult.success(value) : DataResult.error(() -> {
                return messageFactory.apply(value);
            });
        });
    }

    public static Codec<Integer> intRange(int min, int max) {
        return intRangeWithMessage(min, max, (value) -> {
            return "Value must be within range [" + min + ";" + max + "]: " + value;
        });
    }

    private static Codec<Float> floatRangeMinExclusiveWithMessage(float min, float max, Function<Float, String> messageFactory) {
        return validate(Codec.FLOAT, (value) -> {
            return value.compareTo(min) > 0 && value.compareTo(max) <= 0 ? DataResult.success(value) : DataResult.error(() -> {
                return messageFactory.apply(value);
            });
        });
    }

    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> originalCodec) {
        return validate(originalCodec, (list) -> {
            return list.isEmpty() ? DataResult.error(() -> {
                return "List must have contents";
            }) : DataResult.success(list);
        });
    }

    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> originalCodec) {
        return validate(originalCodec, (entryList) -> {
            return entryList.unwrap().right().filter(List::isEmpty).isPresent() ? DataResult.error(() -> {
                return "List must have contents";
            }) : DataResult.success(entryList);
        });
    }

    public static <T> Codec<T> recursive(String name, Function<Codec<T>, Codec<T>> codecFunction) {
        return new ExtraCodecs.RecursiveCodec<>(name, codecFunction);
    }

    public static <A> Codec<A> lazyInitializedCodec(Supplier<Codec<A>> supplier) {
        return new ExtraCodecs.RecursiveCodec<>(supplier.toString(), (codec) -> {
            return supplier.get();
        });
    }

    public static <A> MapCodec<Optional<A>> strictOptionalField(Codec<A> codec, String field) {
        return new ExtraCodecs.StrictOptionalFieldCodec<>(field, codec);
    }

    public static <A> MapCodec<A> strictOptionalField(Codec<A> codec, String field, A fallback) {
        return strictOptionalField(codec, field).xmap((value) -> {
            return value.orElse(fallback);
        }, (value) -> {
            return Objects.equals(value, fallback) ? Optional.empty() : Optional.of(value);
        });
    }

    public static <E> MapCodec<E> retrieveContext(final Function<DynamicOps<?>, DataResult<E>> retriever) {
        class ContextRetrievalCodec extends MapCodec<E> {
            public <T> RecordBuilder<T> encode(E object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                return recordBuilder;
            }

            public <T> DataResult<E> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                return retriever.apply(dynamicOps);
            }

            public String toString() {
                return "ContextRetrievalCodec[" + retriever + "]";
            }

            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return Stream.empty();
            }
        }

        return new ContextRetrievalCodec();
    }

    public static <E, L extends Collection<E>, T> Function<L, DataResult<L>> ensureHomogenous(Function<E, T> typeGetter) {
        return (collection) -> {
            Iterator<E> iterator = collection.iterator();
            if (iterator.hasNext()) {
                T object = typeGetter.apply(iterator.next());

                while(iterator.hasNext()) {
                    E object2 = iterator.next();
                    T object3 = typeGetter.apply(object2);
                    if (object3 != object) {
                        return DataResult.error(() -> {
                            return "Mixed type list: element " + object2 + " had type " + object3 + ", but list is of type " + object;
                        });
                    }
                }
            }

            return DataResult.success(collection, Lifecycle.stable());
        };
    }

    public static <A> Codec<A> catchDecoderException(final Codec<A> codec) {
        return Codec.of(codec, new Decoder<A>() {
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> dynamicOps, T object) {
                try {
                    return codec.decode(dynamicOps, object);
                } catch (Exception var4) {
                    return DataResult.error(() -> {
                        return "Caught exception decoding " + object + ": " + var4.getMessage();
                    });
                }
            }
        });
    }

    public static Codec<TemporalAccessor> temporalCodec(DateTimeFormatter formatter) {
        return Codec.STRING.comapFlatMap((string) -> {
            try {
                return DataResult.success(formatter.parse(string));
            } catch (Exception var3) {
                return DataResult.error(var3::getMessage);
            }
        }, formatter::format);
    }

    public static MapCodec<OptionalLong> asOptionalLong(MapCodec<Optional<Long>> codec) {
        return codec.xmap(toOptionalLong, fromOptionalLong);
    }

    public static Codec<String> sizeLimitedString(int minLength, int maxLength) {
        return validate(Codec.STRING, (string) -> {
            int k = string.length();
            if (k < minLength) {
                return DataResult.error(() -> {
                    return "String \"" + string + "\" is too short: " + k + ", expected range [" + minLength + "-" + maxLength + "]";
                });
            } else {
                return k > maxLength ? DataResult.error(() -> {
                    return "String \"" + string + "\" is too long: " + k + ", expected range [" + minLength + "-" + maxLength + "]";
                }) : DataResult.success(string);
            }
        });
    }

    public static <T> Codec<T> withAlternative(Codec<T> a, Codec<? extends T> b) {
        return Codec.either(a, b).xmap((either) -> {
            return either.map((o) -> {
                return o;
            }, (o) -> {
                return o;
            });
        }, Either::left);
    }

    public static <T, U> Codec<T> withAlternative(Codec<T> serialized, Codec<U> alternative, Function<U, T> alternativeMapper) {
        return Codec.either(serialized, alternative).xmap((either) -> {
            return either.map((o) -> {
                return o;
            }, alternativeMapper);
        }, Either::left);
    }

    public static <T> Codec<Object2BooleanMap<T>> object2BooleanMap(Codec<T> keyCodec) {
        return Codec.unboundedMap(keyCodec, Codec.BOOL).xmap(Object2BooleanOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    /** @deprecated */
    @Deprecated
    public static <K, V> MapCodec<V> dispatchOptionalValue(final String typeKey, final String parametersKey, final Codec<K> typeCodec, final Function<? super V, ? extends K> typeGetter, final Function<? super K, ? extends Codec<? extends V>> parametersCodecGetter) {
        return new MapCodec<V>() {
            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return Stream.of(dynamicOps.createString(typeKey), dynamicOps.createString(parametersKey));
            }

            public <T> DataResult<V> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                T object = mapLike.get(typeKey);
                return object == null ? DataResult.error(() -> {
                    return "Missing \"" + typeKey + "\" in: " + mapLike;
                }) : typeCodec.decode(dynamicOps, object).flatMap((pair) -> {
                    // Gale start - dev import deobfuscation fixes
                    T object2 = Objects.requireNonNullElseGet(mapLike.get(parametersKey), dynamicOps::emptyMap);
                    return parametersCodecGetter.apply(pair.getFirst()).decode(dynamicOps, object2).map(Pair::getFirst);
                    // Gale end - dev import deobfuscation fixes
                });
            }

            public <T> RecordBuilder<T> encode(V object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                K object2 = typeGetter.apply(object);
                recordBuilder.add(typeKey, typeCodec.encodeStart(dynamicOps, object2));
                DataResult<T> dataResult = this.encode(parametersCodecGetter.apply(object2), object, dynamicOps);
                if (dataResult.result().isEmpty() || !Objects.equals(dataResult.result().get(), dynamicOps.emptyMap())) {
                    recordBuilder.add(parametersKey, dataResult);
                }

                return recordBuilder;
            }

            private <T, V2 extends V> DataResult<T> encode(Codec<V2> codec, V value, DynamicOps<T> ops) {
                return codec.encodeStart(ops, (V2) value); // Gale - dev import deobfuscation fixes
            }
        };
    }

    public static final class EitherCodec<F, S> implements Codec<Either<F, S>> {
        private final Codec<F> first;
        private final Codec<S> second;

        public EitherCodec(Codec<F> first, Codec<S> second) {
            this.first = first;
            this.second = second;
        }

        public <T> DataResult<Pair<Either<F, S>, T>> decode(DynamicOps<T> dynamicOps, T object) {
            DataResult<Pair<Either<F, S>, T>> dataResult = this.first.decode(dynamicOps, object).map((pair) -> {
                return pair.mapFirst(Either::left);
            });
            if (dataResult.error().isEmpty()) {
                return dataResult;
            } else {
                DataResult<Pair<Either<F, S>, T>> dataResult2 = this.second.decode(dynamicOps, object).map((pair) -> {
                    return pair.mapFirst(Either::right);
                });
                return dataResult2.error().isEmpty() ? dataResult2 : dataResult.apply2((pair, pair2) -> {
                    return pair2;
                }, dataResult2);
            }
        }

        public <T> DataResult<T> encode(Either<F, S> either, DynamicOps<T> dynamicOps, T object) {
            return either.map((left) -> {
                return this.first.encode(left, dynamicOps, object);
            }, (right) -> {
                return this.second.encode(right, dynamicOps, object);
            });
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                ExtraCodecs.EitherCodec<?, ?> eitherCodec = (ExtraCodecs.EitherCodec)object;
                return Objects.equals(this.first, eitherCodec.first) && Objects.equals(this.second, eitherCodec.second);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.first, this.second);
        }

        @Override
        public String toString() {
            return "EitherCodec[" + this.first + ", " + this.second + "]";
        }
    }

    static class RecursiveCodec<T> implements Codec<T> {
        private final String name;
        private final Supplier<Codec<T>> wrapped;

        RecursiveCodec(String name, Function<Codec<T>, Codec<T>> codecFunction) {
            this.name = name;
            this.wrapped = Suppliers.memoize(() -> {
                return codecFunction.apply(this);
            });
        }

        public <S> DataResult<Pair<T, S>> decode(DynamicOps<S> dynamicOps, S object) {
            return this.wrapped.get().decode(dynamicOps, object);
        }

        public <S> DataResult<S> encode(T object, DynamicOps<S> dynamicOps, S object2) {
            return this.wrapped.get().encode(object, dynamicOps, object2);
        }

        @Override
        public String toString() {
            return "RecursiveCodec[" + this.name + "]";
        }
    }

    static final class StrictOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
        private final String name;
        private final Codec<A> elementCodec;

        public StrictOptionalFieldCodec(String field, Codec<A> codec) {
            this.name = field;
            this.elementCodec = codec;
        }

        public <T> DataResult<Optional<A>> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
            T object = mapLike.get(this.name);
            return object == null ? DataResult.success(Optional.empty()) : this.elementCodec.parse(dynamicOps, object).map(Optional::of);
        }

        public <T> RecordBuilder<T> encode(Optional<A> optional, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
            return optional.isPresent() ? recordBuilder.add(this.name, this.elementCodec.encodeStart(dynamicOps, optional.get())) : recordBuilder;
        }

        public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
            return Stream.of(dynamicOps.createString(this.name));
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (!(object instanceof ExtraCodecs.StrictOptionalFieldCodec)) {
                return false;
            } else {
                ExtraCodecs.StrictOptionalFieldCodec<?> strictOptionalFieldCodec = (ExtraCodecs.StrictOptionalFieldCodec)object;
                return Objects.equals(this.name, strictOptionalFieldCodec.name) && Objects.equals(this.elementCodec, strictOptionalFieldCodec.elementCodec);
            }
        }

        public int hashCode() {
            return Objects.hash(this.name, this.elementCodec);
        }

        public String toString() {
            return "StrictOptionalFieldCodec[" + this.name + ": " + this.elementCodec + "]";
        }
    }

    public static record StrictUnboundedMapCodec<K, V>(Codec<K> keyCodec, Codec<V> elementCodec) implements Codec<Map<K, V>>, BaseMapCodec<K, V> {
        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
            ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();

            for(Pair<T, T> pair : mapLike.entries().toList()) {
                DataResult<K> dataResult = this.keyCodec().parse(dynamicOps, pair.getFirst());
                DataResult<V> dataResult2 = this.elementCodec().parse(dynamicOps, pair.getSecond());
                DataResult<Pair<K, V>> dataResult3 = dataResult.apply2stable(Pair::of, dataResult2);
                if (dataResult3.error().isPresent()) {
                    return DataResult.error(() -> {
                        DataResult.PartialResult<Pair<K, V>> partialResult = dataResult3.error().get();
                        String string;
                        if (dataResult.result().isPresent()) {
                            string = "Map entry '" + dataResult.result().get() + "' : " + partialResult.message();
                        } else {
                            string = partialResult.message();
                        }

                        return string;
                    });
                }

                if (!dataResult3.result().isPresent()) {
                    return DataResult.error(() -> {
                        return "Empty or invalid map contents are not allowed";
                    });
                }

                Pair<K, V> pair2 = dataResult3.result().get();
                builder.put(pair2.getFirst(), pair2.getSecond());
            }

            Map<K, V> map = builder.build();
            return DataResult.success(map);
        }

        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> dynamicOps, T object) {
            return dynamicOps.getMap(object).setLifecycle(Lifecycle.stable()).flatMap((map) -> {
                return this.decode(dynamicOps, map);
            }).map((map) -> {
                return Pair.of(map, object);
            });
        }

        public <T> DataResult<T> encode(Map<K, V> map, DynamicOps<T> dynamicOps, T object) {
            return this.encode(map, dynamicOps, dynamicOps.mapBuilder()).build(object);
        }

        @Override
        public String toString() {
            return "StrictUnboundedMapCodec[" + this.keyCodec + " -> " + this.elementCodec + "]";
        }
    }

    public static record TagOrElementLocation(ResourceLocation id, boolean tag) {
        @Override
        public String toString() {
            return this.decoratedId();
        }

        private String decoratedId() {
            return this.tag ? "#" + this.id : this.id.toString();
        }
    }

    static record XorCodec<F, S>(Codec<F> first, Codec<S> second) implements Codec<Either<F, S>> {
        public <T> DataResult<Pair<Either<F, S>, T>> decode(DynamicOps<T> dynamicOps, T object) {
            DataResult<Pair<Either<F, S>, T>> dataResult = this.first.decode(dynamicOps, object).map((pair) -> {
                return pair.mapFirst(Either::left);
            });
            DataResult<Pair<Either<F, S>, T>> dataResult2 = this.second.decode(dynamicOps, object).map((pair) -> {
                return pair.mapFirst(Either::right);
            });
            Optional<Pair<Either<F, S>, T>> optional = dataResult.result();
            Optional<Pair<Either<F, S>, T>> optional2 = dataResult2.result();
            if (optional.isPresent() && optional2.isPresent()) {
                return DataResult.error(() -> {
                    return "Both alternatives read successfully, can not pick the correct one; first: " + optional.get() + " second: " + optional2.get();
                }, optional.get());
            } else if (optional.isPresent()) {
                return dataResult;
            } else {
                return optional2.isPresent() ? dataResult2 : dataResult.apply2((a, b) -> {
                    return b;
                }, dataResult2);
            }
        }

        public <T> DataResult<T> encode(Either<F, S> either, DynamicOps<T> dynamicOps, T object) {
            return either.map((left) -> {
                return this.first.encode(left, dynamicOps, object);
            }, (right) -> {
                return this.second.encode(right, dynamicOps, object);
            });
        }

        @Override
        public String toString() {
            return "XorCodec[" + this.first + ", " + this.second + "]";
        }
    }
}
