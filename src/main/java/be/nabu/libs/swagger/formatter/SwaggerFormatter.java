package be.nabu.libs.swagger.formatter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.swagger.api.SwaggerDefinition;
import be.nabu.libs.swagger.api.SwaggerDocumentation;
import be.nabu.libs.swagger.api.SwaggerInfo;
import be.nabu.libs.swagger.api.SwaggerMethod;
import be.nabu.libs.swagger.api.SwaggerParameter;
import be.nabu.libs.swagger.api.SwaggerParameter.ParameterLocation;
import be.nabu.libs.swagger.api.SwaggerParameter.ParameterSubType;
import be.nabu.libs.swagger.api.SwaggerParameter.ParameterType;
import be.nabu.libs.swagger.api.SwaggerPath;
import be.nabu.libs.swagger.api.SwaggerResponse;
import be.nabu.libs.swagger.api.SwaggerSecurityDefinition;
import be.nabu.libs.swagger.api.SwaggerSecuritySetting;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.binding.json.JSONBinding;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.map.MapContent;
import be.nabu.libs.types.map.MapContentWrapper;
import be.nabu.libs.types.map.MapType;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.MaxExclusiveProperty;
import be.nabu.libs.types.properties.MaxInclusiveProperty;
import be.nabu.libs.types.properties.MaxLengthProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinExclusiveProperty;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.types.properties.MinLengthProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.PatternProperty;

public class SwaggerFormatter {
	
	private boolean expandInline;
	private boolean allowDefinedTypeReferences;
	private List<DefinedType> referencedTypes;
	
//	public static void main(String...args) throws IOException {
//		URL url = new URL("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v2.0/json/petstore.json");
////		url = new URL("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v2.0/json/petstore-expanded.json");
//		InputStream openStream = url.openStream();
//		try {
//			SwaggerDefinition definition = new be.nabu.libs.swagger.parser.SwaggerParser().parse("my.swagger", openStream);
//			ByteArrayOutputStream output = new ByteArrayOutputStream();
//			new SwaggerFormatter().format(definition, output);
//			System.out.println(new String(output.toByteArray()));
//		}
//		finally {
//			openStream.close();
//		}
//	}
	
	@SuppressWarnings({ "incomplete-switch", "unchecked" })
	public void format(SwaggerDefinition definition, OutputStream output) throws IOException {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("swagger", definition.getVersion());
		if (definition.getInfo() != null) {
			map.put("info", new BeanInstance<SwaggerInfo>(definition.getInfo()));
		}
		map.put("host", definition.getHost());
		map.put("basePath", definition.getBasePath());
		map.put("schemes", definition.getSchemes());
		map.put("consumes", definition.getConsumes());
		map.put("produces", definition.getProduces());
		
		referencedTypes = new ArrayList<DefinedType>();
		if (definition.getPaths() != null) {
			Map<String, Object> pathMap = new LinkedHashMap<String, Object>();
			for (SwaggerPath path : definition.getPaths()) {
				Map<String, Object> methods = new LinkedHashMap<String, Object>();
				if (path.getMethods() != null) {
					for (SwaggerMethod swaggerMethod : path.getMethods()) {
						Map<String, Object> method = new LinkedHashMap<String, Object>();
						method.put("summary", swaggerMethod.getSummary());
						method.put("description", swaggerMethod.getDescription());
						method.put("operationId", swaggerMethod.getOperationId());
						method.put("consumes", swaggerMethod.getConsumes());
						method.put("produces", swaggerMethod.getProduces());
						method.put("deprecated", swaggerMethod.getDeprecated());
						method.put("tags", swaggerMethod.getTags());
						if (swaggerMethod.getDocumentation() != null) {
							method.put("externalDocs", new BeanInstance<SwaggerDocumentation>(swaggerMethod.getDocumentation()));
						}
						
						if (swaggerMethod.getParameters() != null) {
							List<Object> parameters = new ArrayList<Object>();
							for (SwaggerParameter parameter : swaggerMethod.getParameters()) {
								parameters.add(formatParameter(definition, parameter));
							}
							method.put("parameters", parameters);
						}
						method.put("schemes", swaggerMethod.getSchemes());

						if (swaggerMethod.getResponses() != null) {
							Map<String, Object> allResponses = new LinkedHashMap<String, Object>();
							for (SwaggerResponse response : swaggerMethod.getResponses()) {
								String code = response.getCode() == null ? "default" : response.getCode().toString();
								Map<String, Object> responseContent = new LinkedHashMap<String, Object>();
								responseContent.put("description", response.getDescription());
								if (response.getHeaders() != null) {
									Map<String, Object> headerContent = new LinkedHashMap<String, Object>();
									for (SwaggerParameter header : response.getHeaders()) {
										Map<String, Object> formatParameter = formatParameter(definition, header);
										formatParameter.remove("name");
										headerContent.put(header.getName(), formatParameter);
									}
									responseContent.put("headers", headerContent);
								}
								if (response.getElement() != null) {
									responseContent.put("schema", formatResponseSchema(definition, response));
								}
								allResponses.put(code, responseContent);
							}
							method.put("responses", allResponses);
						}
						
						if (swaggerMethod.getSecurity() != null) {
							List<Object> securities = new ArrayList<Object>();
							for (SwaggerSecuritySetting securitySetting : swaggerMethod.getSecurity()) {
								Map<String, Object> security = new LinkedHashMap<String, Object>();
								security.put(securitySetting.getName(), securitySetting.getScopes() == null ? new ArrayList<String>() : securitySetting.getScopes());
								securities.add(security);
							}
							if (!securities.isEmpty()) {
								method.put("security", securities);
							}
						}
						methods.put(swaggerMethod.getMethod(), method);
					}
				}
				pathMap.put(path.getPath(), methods);
				map.put("paths", pathMap);
			}
		}
		if (definition.getRegistry() != null) {
			Map<String, Object> elements = new LinkedHashMap<String, Object>();
			for (ComplexType complexType : definition.getRegistry().getComplexTypes(definition.getId())) {
				Map<String, Object> elementMap = formatDefinedType(definition, complexType);
				elements.put(complexType.getName(), elementMap);
			}
			for (SimpleType<?> simpleType : definition.getRegistry().getSimpleTypes(definition.getId())) {
				Map<String, Object> elementMap = formatDefinedType(definition, simpleType);
				elements.put(simpleType.getName(), elementMap);
			}
			map.put("definitions", elements);
		}

		// this is a recursive operation as writing out the types can introduce new dependencies to other types
		while (!referencedTypes.isEmpty()) {
			List<DefinedType> currentBatch = new ArrayList<DefinedType>(referencedTypes);
			referencedTypes.clear();
			Map<String, Object> definitions = (Map<String, Object>) map.get("definitions");
			if (definitions == null) {
				definitions = new LinkedHashMap<String, Object>();
				map.put("definitions", definitions);
			}
			for (DefinedType referencedType : currentBatch) {
				// it is not defined yet
				if (!definitions.containsKey(referencedType.getId())) {
					definitions.put(referencedType.getId(), formatDefinedType(definition, referencedType));
				}
			}
		}
		
		if (definition.getSecurityDefinitions() != null) {
			Map<String, Object> security = new LinkedHashMap<String, Object>();
			for (SwaggerSecurityDefinition securityDefinition : definition.getSecurityDefinitions()) {
				Map<String, Object> securityContent = new LinkedHashMap<String, Object>();
				securityContent.put("type", securityDefinition.getType().toString());
				securityContent.put("description", securityDefinition.getDescription());
				switch(securityDefinition.getType()) {
					case apiKey:
						securityContent.put("name", securityDefinition.getFieldName());
						if (securityDefinition.getLocation() != null) {
							securityContent.put("in", securityDefinition.getLocation().toString());
						}
					break;
					case oauth2:
						if (securityDefinition.getFlow() != null) {
							securityContent.put("flow", securityDefinition.getFlow().toString());
						}
						securityContent.put("tokenUrl", securityDefinition.getTokenUrl());
						securityContent.put("authorizationUrl", securityDefinition.getAuthorizationUrl());
						securityContent.put("scopes", securityDefinition.getScopes());
					break;
				}
				security.put(securityDefinition.getName(), securityContent);
			}
			map.put("securityDefinitions", security);
		}
		
		MapType content = MapContentWrapper.buildFromContent(map);
		JSONBinding binding = new JSONBinding(content);
		binding.setPrettyPrint(true);
		binding.setAllowRaw(true);
		binding.marshal(output, new MapContent(content, map));
	}

	private Map<String, Object> formatResponseSchema(SwaggerDefinition definition, SwaggerResponse response) {
		Map<String, Object> schema = new LinkedHashMap<String, Object>();
		Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), response.getElement().getProperties());
		if (maxOccurs != null && maxOccurs != 1) {
			schema.put("type", "array");
			Map<String, Object> items = new LinkedHashMap<String, Object>();
			Integer typeMaxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), response.getElement().getType().getProperties());
			// we are using type extension to add the max occurs, presumably because it is from a parsed swagger file, internally we never do this
			Type targetType;
			if (typeMaxOccurs != null && typeMaxOccurs != 1) {
				targetType = response.getElement().getType().getSuperType();
			}
			// probably doing internal stuff, check the type namespace
			else {
				targetType = response.getElement().getType();
			}
			if (!definition.getId().equals(targetType.getNamespace())) {
				formatCommonProperties(targetType, items, true, response.getElement().getProperties());
			}
			else {
				items.put("$ref", "#/definitions/" + targetType.getName());
			}
			schema.put("items", items);
		}
		else if (response.getElement().getType() instanceof ComplexType) {
			if (!definition.getId().equals(response.getElement().getType().getNamespace())) {
				throw new IllegalArgumentException("A complex response can only exist as a reference to a defined type: " + response.getCode());
			}
			schema.put("$ref", "#/definitions/" + response.getElement().getType().getName());
		}
		else if (definition.getId().equals(response.getElement().getType().getNamespace())) {
			schema.put("$ref", "#/definitions/" + response.getElement().getType().getName());
		}
		else {
			formatCommonProperties(response.getElement().getType(), schema, true, response.getElement().getProperties());
		}
		return schema;
	}
	
	private Map<String, Object> formatParameter(SwaggerDefinition definition, SwaggerParameter parameter) {
		Map<String, Object> content = new LinkedHashMap<String, Object>();
		if (parameter.getLocation() != null) {
			content.put("in", parameter.getLocation().toString());
		}
		Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), parameter.getElement().getProperties());
		if (maxOccurs != null && maxOccurs != 1) {
			content.put("type", "array");
			Map<String, Object> items = new LinkedHashMap<String, Object>();
			Integer typeMaxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), parameter.getElement().getType().getProperties());
			// we are using type extension to add the max occurs, presumably because it is from a parsed swagger file, internally we never do this
			Type targetType;
			if (typeMaxOccurs != null && typeMaxOccurs != 1) {
				targetType = parameter.getElement().getType().getSuperType();
			}
			// probably doing internal stuff, check the type namespace
			else {
				targetType = parameter.getElement().getType();
			}
			if (!definition.getId().equals(targetType.getNamespace())) {
				formatCommonProperties(targetType, items, true, parameter.getElement().getProperties());
			}
			else {
				items.put("$ref", "#/definitions/" + targetType.getName());
			}
			content.put("items", items);
		}
		else if (parameter.getElement().getType() instanceof ComplexType) {
			if (!ParameterLocation.BODY.equals(parameter.getLocation())) {
				throw new IllegalArgumentException("A complex input parameter can only exist in the body: " + parameter.getName());
			}
			if (!definition.getId().equals(parameter.getElement().getType().getNamespace())) {
				throw new IllegalArgumentException("A complex body input parameter can only exist as a reference to a defined type: " + parameter.getName());
			}
			Map<String, Object> schema = new LinkedHashMap<String, Object>();
			schema.put("$ref", "#/definitions/" + parameter.getElement().getType().getName());
			content.put("schema", schema);
		}
		else if (definition.getId().equals(parameter.getElement().getType().getNamespace())) {
			Map<String, Object> schema = new LinkedHashMap<String, Object>();
			schema.put("$ref", "#/definitions/" + parameter.getElement().getType().getName());
			content.put("schema", schema);
		}
		else {
			formatCommonProperties(parameter.getElement().getType(), content, false, parameter.getElement().getProperties());
		}
		// put the actual name as declared
		content.put("name", parameter.getName());
		content.put("allowEmptyValue", parameter.getAllowEmptyValue());
		content.put("default", parameter.getDefaultValue());
		content.put("uniqueItems", parameter.getUnique());
		content.put("multipleOf", parameter.getMultipleOf());
		if (parameter.getCollectionFormat() != null) {
			content.put("collectionFormat", parameter.getCollectionFormat().toString());
		}
		return content;
	}
	
	public static String formatTypeAsJSON(Type type) {
		Map<String, Object> map = new SwaggerFormatter().formatDefinedType(null, type);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		MapType content = MapContentWrapper.buildFromContent(map);
		JSONBinding binding = new JSONBinding(content);
		binding.setPrettyPrint(true);
		binding.setAllowRaw(true);
		try {
			binding.marshal(output, new MapContent(content, map));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new String(output.toByteArray(), Charset.forName("UTF-8"));
	}
	
	private Map<String, Object> formatDefinedType(SwaggerDefinition definition, Type type) {
		Map<String, Object> content = new LinkedHashMap<String, Object>();
		Integer minOccurs = ValueUtils.getValue(MinOccursProperty.getInstance(), type.getProperties());
		Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), type.getProperties());
		// we have an array, this is represented by a type that extends another type
		if (maxOccurs != null && maxOccurs != 1) {
			Type superType = type.getSuperType();
			content.put("type", "array");
			if (maxOccurs != 0) {
				content.put("maxItems", maxOccurs);
			}
			content.put("minItems", minOccurs == null ? 1 : minOccurs);
			// if it is a not a type that is defined in this definition, unfold it internally
			if (definition == null || !definition.getId().equals(superType.getNamespace())) {
				content.put("items", formatDefinedType(definition, superType));
			}
			else {
				Map<String, Object> items = new LinkedHashMap<String, Object>();
				items.put("$ref", "#/definitions/" + superType.getName());
				content.put("items", items);
			}
		}
		else {
			boolean expandInline = this.expandInline;
			Map<String, Object> targetMap;
			// if the supertype is a type that exists within the definition, reference it
			if (!expandInline && type.getSuperType() != null && definition != null && definition.getId().equals(type.getSuperType().getNamespace())) {
				List<Object> allOf = new ArrayList<Object>();
				Map<String, Object> parent = new LinkedHashMap<String, Object>();
				parent.put("$ref", "#/definitions/" + type.getSuperType().getName());
				allOf.add(parent);
				Map<String, Object> extension = new LinkedHashMap<String, Object>();
				allOf.add(extension);
				targetMap = extension;
				content.put("type", "object");
				content.put("allOf", allOf);
			}
			else {
				expandInline = true;
				targetMap = content;
			}
			formatCommonProperties(type, targetMap, true, type.getProperties());
			if (type instanceof ComplexType) {
				List<String> required = new ArrayList<String>();
				targetMap.put("required", required);
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				for (Element<?> child : expandInline ? TypeUtils.getAllChildren((ComplexType) type) : (ComplexType) type) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					String name = child.getName();
					boolean isAttribute = false;
					if (name.startsWith("@")) {
						name = name.substring(1);
						isAttribute = true;
					}
					if (property == null || property.getValue() != 0) {
						required.add(name);
					}
					Map<String, Object> childProperties = formatElement(definition, child, true, false);
					properties.put(name, childProperties);
					if (isAttribute) {
						Map<String, Object> xml = new HashMap<String, Object>();
						xml.put("attribute", true);
						xml.put("name", name);
						childProperties.put("xml", xml);
					}
				}
				if (required.isEmpty()) {
					targetMap.remove("required");
				}
				if (!properties.isEmpty()) {
					targetMap.put("properties", properties);
				}
			}
		}
		return content;
	}
	
	private Map<String, Object> formatElement(SwaggerDefinition definition, Element<?> element, boolean isPartOfObject, boolean ignoreMaxOccurs) {
		Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), element.getProperties());
		Map<String, Object> content = new LinkedHashMap<String, Object>();
		if (!ignoreMaxOccurs && maxOccurs != null && maxOccurs != 1) {
			Map<String, Object> items = new LinkedHashMap<String, Object>();
			content.put("type", "array");
			content.put("items", items);
			items.putAll(formatElement(definition, element, isPartOfObject, true));
		}
		else {
			// we are referencing a defined type
			if (definition != null && definition.getId().equals(element.getType().getNamespace())) {
				Map<String, Object> schema = new LinkedHashMap<String, Object>();
				schema.put("$ref", "#/definitions/" + element.getType().getName());
				content.put("schema", schema);
			}
			// currently we don't allow for simple types because this would drag in all the java.lang.String etc of this world
			// this library is not aware of the repository so can not do a quick check if it is a custom element...
			// TODO: should solve this better...
			else if (allowDefinedTypeReferences && element.getType() instanceof ComplexType && element.getType() instanceof DefinedType) {
				if (isPartOfObject) {
					content.put("$ref", "#/definitions/" + ((DefinedType) element.getType()).getId());	
				}
				else {
					Map<String, Object> schema = new LinkedHashMap<String, Object>();
					schema.put("$ref", "#/definitions/" + ((DefinedType) element.getType()).getId());
					content.put("schema", schema);
				}
				// make sure it is (eventually) defined
				referencedTypes.add((DefinedType) element.getType());
			}
			else {
				formatCommonProperties(element.getType(), content, isPartOfObject, element.getProperties());
				
				if (element.getType() instanceof ComplexType) {
					List<String> required = new ArrayList<String>();
					content.put("required", required);
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					for (Element<?> child : TypeUtils.getAllChildren((ComplexType) element.getType())) {
						Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
						String name = child.getName();
						boolean isAttribute = false;
						if (name.startsWith("@")) {
							name = name.substring(1);
							isAttribute = true;
						}
						if (property == null || property.getValue() != 0) {
							required.add(name);
						}
						Map<String, Object> childProperties = formatElement(definition, child, true, false);
						properties.put(name, childProperties);
						if (isAttribute) {
							Map<String, Object> xml = new HashMap<String, Object>();
							xml.put("attribute", true);
							xml.put("name", name);
							childProperties.put("xml", xml);
						}
					}
					if (required.isEmpty()) {
						content.remove("required");
					}
					if (!properties.isEmpty()) {
						content.put("properties", properties);
					}
				}
			}
		}
		return content;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void formatCommonProperties(Type type, Map<String, Object> content, boolean isPartOfObject, Value<?>...properties) {
		Integer minOccurs = ValueUtils.getValue(MinOccursProperty.getInstance(), properties);
		
		if (!isPartOfObject) {
			boolean required = minOccurs == null || minOccurs == 1;
			// default is "false" an some fields (like headers) don't even have the boolean, so don't print it if it doesn't need to be printed
			if (required) {
				content.put("required", required);
			}
			String name = ValueUtils.getValue(NameProperty.getInstance(), properties);
			if (name != null) {
				if (name.startsWith("@")) {
					content.put("name", name.substring(1));
					Map<String, Object> xml = new HashMap<String, Object>();
					xml.put("attribute", true);
					xml.put("name", name.substring(1));
					content.put("xml", xml);
				}
				else {
					content.put("name", name);
				}
			}
		}
		
		Object maxExclusive = ValueUtils.getValue(new MaxExclusiveProperty(), properties);
		if (maxExclusive != null) {
			content.put("maximum", ConverterFactory.getInstance().getConverter().convert(maxExclusive, Double.class));
			content.put("exclusiveMaximum", true);
		}
		else {
			Object maxInclusive = ValueUtils.getValue(new MaxInclusiveProperty(), properties);
			if (maxInclusive != null) {
				content.put("maximum", ConverterFactory.getInstance().getConverter().convert(maxInclusive, Double.class));
				content.put("exclusiveMaximum", false);
			}
		}
		
		Object minExclusive = ValueUtils.getValue(new MinExclusiveProperty(), properties);
		if (minExclusive != null) {
			content.put("minimum", ConverterFactory.getInstance().getConverter().convert(minExclusive, Double.class));
			content.put("exclusiveMinimum", true);
		}
		else {
			Object minInclusive = ValueUtils.getValue(new MinInclusiveProperty(), properties);
			if (minInclusive != null) {
				content.put("minimum", ConverterFactory.getInstance().getConverter().convert(minInclusive, Double.class));
				content.put("exclusiveMinimum", false);
			}
		}
		
		Integer maxLength = ValueUtils.getValue(MaxLengthProperty.getInstance(), properties);
		if (maxLength != null) {
			content.put("maxLength", maxLength);
		}
		
		Integer minLength = ValueUtils.getValue(MinLengthProperty.getInstance(), properties);
		if (minLength != null) {
			content.put("minLength", minLength);
		}
		
		String pattern = ValueUtils.getValue(PatternProperty.getInstance(), properties);
		if (pattern != null) {
			content.put("pattern", pattern);
		}
		
		Object enumeration = ValueUtils.getValue(new EnumerationProperty(), properties);
		if (enumeration != null) {
			content.put("enum", enumeration);
		}
		
		String comment = ValueUtils.getValue(CommentProperty.getInstance(), properties);
		if (comment != null) {
			content.put("description", comment);
		}
		
		if (type instanceof SimpleType) {
			ParameterType mainType = null;
			ParameterSubType subType = null;
			Class<?> instanceClass = ((SimpleType<?>) type).getInstanceClass();
			if (Boolean.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.BOOLEAN;
			}
			else if (byte[].class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.STRING;
				subType = ParameterSubType.BYTE;
			}
			else if (InputStream.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.STRING;
				subType = ParameterSubType.BINARY;
			}
			else if (Date.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.STRING;
				String format = ValueUtils.getValue(FormatProperty.getInstance(), properties);
				if (format != null && format.equals("date")) {
					subType = ParameterSubType.DATE;
				}
				else {
					subType = ParameterSubType.DATE_TIME;
				}
			}
			else if (Integer.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.INTEGER;
				subType = ParameterSubType.INT32;
			}
			else if (Long.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.INTEGER;
				subType = ParameterSubType.INT64;
			}
			else if (Float.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.NUMBER;
				subType = ParameterSubType.FLOAT;
			}
			else if (Double.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.NUMBER;
				subType = ParameterSubType.DOUBLE;
			}
			else if (String.class.isAssignableFrom(instanceClass) || type instanceof Marshallable) {
				mainType = ParameterType.STRING;
			}
			else {
				throw new IllegalArgumentException("No support for: " + instanceClass);
			}
			content.put("type", mainType.toString());
			if (subType != null) {
				content.put("format", subType.toString());
			}
		}
		else {
			content.put("type", "object");
		}
	}
	
	public boolean isExpandInline() {
		return expandInline;
	}

	public void setExpandInline(boolean expandInline) {
		this.expandInline = expandInline;
	}

	public boolean isAllowDefinedTypeReferences() {
		return allowDefinedTypeReferences;
	}

	public void setAllowDefinedTypeReferences(boolean allowDefinedTypeReferences) {
		this.allowDefinedTypeReferences = allowDefinedTypeReferences;
	}
	
}
