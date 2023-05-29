package tools.samt.api.types

interface SamtPackage {
    val name: String
    val qualifiedName: String
    val records: List<RecordType>
    val enums: List<EnumType>
    val services: List<ServiceType>
    val providers: List<ProviderType>
    val consumers: List<ConsumerType>
    val aliases: List<AliasType>
}

/**
 * A SAMT type
 */
interface Type


/**
 * A type reference
 */
interface TypeReference {
    /**
     * The type this reference points to
     */
    val type: Type

    /**
     * Is true if this type reference is optional, meaning it can be null
     */
    val isOptional: Boolean

    /**
     * The range constraints placed on this type, if any
     */
    val rangeConstraint: Constraint.Range?

    /**
     * The size constraints placed on this type, if any
     */
    val sizeConstraint: Constraint.Size?

    /**
     * The pattern constraints placed on this type, if any
     */
    val patternConstraint: Constraint.Pattern?

    /**
     * The value constraints placed on this type, if any
     */
    val valueConstraint: Constraint.Value?

    /**
     * The runtime type this reference points to, could be different from [type] if this is an alias
     */
    val runtimeType: Type

    /**
     * Is true if this type reference or underlying type is optional, meaning it can be null at runtime
     * This is different from [isOptional] in that it will return true for an alias that points to an optional type
     */
    val isRuntimeOptional: Boolean

    /**
     * The runtime range constraints placed on this type, if any.
     * Will differ from [rangeConstraint] if this is an alias
     */
    val runtimeRangeConstraint: Constraint.Range?

    /**
     * The runtime size constraints placed on this type, if any.
     * Will differ from [sizeConstraint] if this is an alias
     */
    val runtimeSizeConstraint: Constraint.Size?

    /**
     * The runtime pattern constraints placed on this type, if any.
     * Will differ from [patternConstraint] if this is an alias
     */
    val runtimePatternConstraint: Constraint.Pattern?

    /**
     * The runtime value constraints placed on this type, if any.
     * Will differ from [valueConstraint] if this is an alias
     */
    val runtimeValueConstraint: Constraint.Value?
}

interface Constraint {
    interface Range : Constraint {
        val lowerBound: Number?
        val upperBound: Number?
    }

    interface Size : Constraint {
        val lowerBound: Long?
        val upperBound: Long?
    }

    interface Pattern : Constraint {
        val pattern: String
    }

    interface Value : Constraint {
        val value: Any
    }
}
