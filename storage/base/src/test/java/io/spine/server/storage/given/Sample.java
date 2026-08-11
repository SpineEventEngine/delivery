/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.given;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.type.TypeUrl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.Messages.builderFor;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * Utility for creating simple stubs for generated messages, DTOs (like {@link Event} and
 * {@link Command}), storage objects and else.
 */
public class Sample {

    /** Prevents instantiation of this utility class. */
    private Sample() {
    }

    /**
     * Generates a new stub {@link Message.Builder} with all the fields set to
     * {@link Random random} values.
     *
     * <p>All the fields are guaranteed to be not {@code null} and not default.
     * Number and {@code boolean} fields may or may not have their default values ({@code 0} and
     * {@code false}).
     *
     * @param clazz
     *         Java class of the stub message
     * @param <M>
     *         type of the required message
     * @param <B>
     *         type of the {@link Message.Builder} for the message
     * @return new instance of the {@link Message.Builder} for given type
     * @apiNote This method casts the builder to the generic parameter {@code <B>} for
     *         brevity of test code. It is the caller responsibility to ensure that the message
     *         type {@code <M>} corresponds to the builder type {@code <B>}.
     * @see #valueFor(FieldDescriptor)
     */
    @SuppressWarnings("TypeParameterUnusedInFormals") // See apiNote.
    public static <M extends Message, B extends Message.Builder> B builderForType(Class<M> clazz) {
        checkClass(clazz);
        @SuppressWarnings("unchecked") // We cast here for brevity of the test code.
        var builder = (B) builderFor(clazz);
        var builderDescriptor = builder.getDescriptorForType();
        var fields = builderDescriptor.getFields();
        for (var field : fields) {
            var value = valueFor(field);
            if (field.isRepeated()) {
                builder.addRepeatedField(field, value);
            } else {
                builder.setField(field, value);
            }
        }
        return builder;
    }

    /**
     * Generates a new stub {@link Message} with all the fields set to {@link Random random} values.
     *
     * <p>All the fields are guaranteed to be not {@code null} and not default.
     * Number and {@code boolean} fields
     * may or may not have their default values ({@code 0} and {@code false}).
     *
     * <p>If the required type is {@link Any}, an instance of an empty {@link Any} wrapped into
     * another {@link Any} is returned. See {@link AnyPacker}.
     *
     * @param clazz
     *         Java class of the required stub message
     * @param <M>
     *         type of the required message
     * @return new instance of the given {@link Message} type with random fields
     * @see #builderForType(Class)
     */
    public static <M extends Message> M messageOfType(Class<M> clazz) {
        checkClass(clazz);

        if (Any.class.equals(clazz)) {
            var any = Any.getDefaultInstance();
            @SuppressWarnings("unchecked") //
            var result = (M) AnyPacker.pack(any);
            return result;
        }

        var builder = builderForType(clazz);
        @SuppressWarnings("unchecked") // Checked cast
        var result = (M) builder.build();

        return result;
    }

    private static void checkClass(Class<? extends Message> clazz) {
        checkNotNull(clazz);
        // Support only generated protobuf messages
        checkArgument(GeneratedMessage.class.isAssignableFrom(clazz),
                      "Only generated protobuf messages are allowed.");
    }

    /**
     * Generates a non-default value for the given message field.
     *
     * <p>All the protobuf types are supported including nested {@link Message}s and
     * the {@code enum}s.
     *
     * @param field
     *         {@link FieldDescriptor} to take the type info from
     * @return a non-default generated value of type of the given field
     */
    @SuppressWarnings("BadImport" /* Use `Type` for brevity. */)
    private static Object valueFor(FieldDescriptor field) {
        var type = field.getType();
        var javaType = type.getJavaType();
        Random random = new SecureRandom();
        return switch (javaType) {
            case INT -> random.nextInt();
            case LONG -> random.nextLong();
            case FLOAT -> random.nextFloat();
            case DOUBLE -> random.nextDouble();
            case BOOLEAN -> random.nextBoolean();
            case STRING -> {
                var bytes = new byte[8];
                random.nextBytes(bytes);
                yield new String(bytes, StandardCharsets.UTF_8);
            }
            case BYTE_STRING -> {
                var bytesPrimitive = new byte[8];
                random.nextBytes(bytesPrimitive);
                yield ByteString.copyFrom(bytesPrimitive);
            }
            case ENUM -> enumValueFor(field, random);
            case MESSAGE -> messageValueFor(field);
        };
    }

    /**
     * Generates a random enum value for the specified {@code field}.
     *
     * <p>The value under index 0 is usually used to store the `undefined` option so it is skipped.
     * Use values with indexes from 1 to n.
     */
    private static Object enumValueFor(FieldDescriptor field, Random random) {
        var descriptor = field.getEnumType();
        var enumValues = descriptor.getValues();
        if (enumValues.isEmpty()) {
            throw newIllegalStateException(
                    "There must be at least one `Enum` value for field `%s`.", field
            );
        }
        var index = random.nextInt(enumValues.size() - 1) + 1;
        var enumValue = descriptor.findValueByNumber(index);
        return enumValue;
    }

    private static Message messageValueFor(FieldDescriptor field) {
        var messageType = TypeUrl.from(field.getMessageType());
        Class<? extends Message> javaClass = messageType.getMessageClass();
        var fieldValue = messageOfType(javaClass);
        return fieldValue;
    }
}
