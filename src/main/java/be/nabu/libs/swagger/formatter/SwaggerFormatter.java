/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.swagger.formatter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
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
import be.nabu.libs.swagger.api.SwaggerTag;
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
import be.nabu.libs.types.properties.DynamicNameProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.LengthProperty;
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
import be.nabu.libs.types.utils.DateUtils;
import be.nabu.libs.types.utils.DateUtils.Granularity;

public class SwaggerFormatter {
	
	private boolean expandInline;
	private boolean allowDefinedTypeReferences;
	private List<DefinedType> referencedTypes;
	private boolean allowCustomFormats = true;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private boolean includeDocumentation = true;
	
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
		if (definition.getInfo() != null && includeDocumentation) {
			map.put("info", new BeanInstance<SwaggerInfo>(definition.getInfo()));
		}
		map.put("host", definition.getHost());
		map.put("basePath", definition.getBasePath());
		map.put("schemes", definition.getSchemes());
		map.put("consumes", definition.getConsumes());
		map.put("produces", definition.getProduces());
		
		if (includeDocumentation) {
			List<SwaggerTag> globalTags = definition.getTags();
			if (globalTags != null && !globalTags.isEmpty()) {
				List<Object> tagList = new ArrayList<Object>();
				for (SwaggerTag globalTag : globalTags) {
					Map<String, Object> singleTagMap = new LinkedHashMap<String, Object>();
					singleTagMap.put("name", globalTag.getName());
					singleTagMap.put("description", globalTag.getDescription());
					tagList.add(singleTagMap);
				}
				map.put("tags", tagList);
			}
		}
		
		referencedTypes = new ArrayList<DefinedType>();
		if (definition.getPaths() != null) {
			Map<String, Object> pathMap = new LinkedHashMap<String, Object>();
			for (SwaggerPath path : definition.getPaths()) {
				// if you have multiple path objects linking to the same actual path on the web application, merge them 
				Map<String, Object> methods = (Map<String, Object>) pathMap.get(path.getPath());
				if (methods == null) {
					methods = new LinkedHashMap<String, Object>();
				}
				try {
					if (path.getMethods() != null) {
						for (SwaggerMethod swaggerMethod : path.getMethods()) {
							Map<String, Object> method = new LinkedHashMap<String, Object>();
							if (includeDocumentation) {
								method.put("summary", swaggerMethod.getSummary());
								method.put("description", swaggerMethod.getDescription());
							}
							method.put("operationId", swaggerMethod.getOperationId());
							method.put("consumes", swaggerMethod.getConsumes());
							method.put("produces", swaggerMethod.getProduces());
							method.put("deprecated", swaggerMethod.getDeprecated());
							method.put("tags", swaggerMethod.getTags());
							if (swaggerMethod.getDocumentation() != null && includeDocumentation) {
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
									if (includeDocumentation) {
										responseContent.put("description", response.getDescription());
									}
									if (response.getHeaders() != null) {
										Map<String, Object> headerContent = new LinkedHashMap<String, Object>();
										for (SwaggerParameter header : response.getHeaders()) {
											Map<String, Object> formatParameter = formatParameter(definition, header);
											formatParameter.remove("name");
											// should not put the "required" attribute in headers (at least in response), this is not allowed according to the spec
											formatParameter.remove("required");
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
							
							// set extensions
							if (swaggerMethod.getExtensions() != null) {
								for (String key : swaggerMethod.getExtensions().keySet()) {
									method.put("x-" + key, swaggerMethod.getExtensions().get(key));
								}
							}
							
							methods.put(swaggerMethod.getMethod(), method);
						}
					}
				}
				catch (Exception e) {
					logger.error("Could not format operation: " + path.getPath(), e);
					throw new RuntimeException(e);
				}
				pathMap.put(path.getPath(), methods);
				map.put("paths", pathMap);
			}
		}
		if (definition.getRegistry() != null) {
			Map<String, Object> elements = new LinkedHashMap<String, Object>();
			for (ComplexType complexType : definition.getRegistry().getComplexTypes(definition.getId())) {
				try {
					Map<String, Object> elementMap = formatDefinedType(definition, complexType, true);
					elements.put(complexType.getName(), elementMap);
				}
				catch (Exception e) {
					logger.error("Could not format complex type: " + complexType.getName(), e);
					throw new RuntimeException(e);
				}
			}
			for (SimpleType<?> simpleType : definition.getRegistry().getSimpleTypes(definition.getId())) {
				try {
					Map<String, Object> elementMap = formatDefinedType(definition, simpleType, true);
					elements.put(simpleType.getName(), elementMap);
				}
				catch (Exception e) {
					logger.error("Could not format simple type: " + simpleType.getName(), e);
					throw new RuntimeException(e);
				}
			}
			map.put("definitions", elements);
		}
		
		if (definition.getGlobalSecurity() != null) {
			List<Object> securities = new ArrayList<Object>();
			for (SwaggerSecuritySetting securitySetting : definition.getGlobalSecurity()) {
				Map<String, Object> security = new LinkedHashMap<String, Object>();
				security.put(securitySetting.getName(), securitySetting.getScopes() == null ? new ArrayList<String>() : securitySetting.getScopes());
				securities.add(security);
			}
			if (!securities.isEmpty()) {
				map.put("security", securities);
			}
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
					definitions.put(referencedType.getId(), formatDefinedType(definition, referencedType, true));
				}
			}
		}
		
		if (definition.getSecurityDefinitions() != null) {
			Map<String, Object> security = new LinkedHashMap<String, Object>();
			for (SwaggerSecurityDefinition securityDefinition : definition.getSecurityDefinitions()) {
				Map<String, Object> securityContent = new LinkedHashMap<String, Object>();
				securityContent.put("type", securityDefinition.getType().toString());
				if (includeDocumentation) {
					securityContent.put("description", securityDefinition.getDescription());
				}
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
			content.put("collectionFormat", parameter.getCollectionFormat().toString().toLowerCase());
		}
		return content;
	}
	
	public static String formatTypeAsJSON(Type type) {
		return formatTypeAsJSON(type, true);
	}
	
	public static String formatTypeAsJSON(Type type, boolean pretty) {
		Map<String, Object> map = new SwaggerFormatter().formatDefinedType(null, type, true);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		MapType content = MapContentWrapper.buildFromContent(map);
		JSONBinding binding = new JSONBinding(content);
		binding.setPrettyPrint(pretty);
		binding.setAllowRaw(true);
		try {
			binding.marshal(output, new MapContent(content, map));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new String(output.toByteArray(), Charset.forName("UTF-8"));
	}
	
	private Map<String, Object> formatDefinedType(SwaggerDefinition definition, Type type, boolean isRoot) {
		Map<String, Object> content = new LinkedHashMap<String, Object>();
		Integer minOccurs = ValueUtils.getValue(MinOccursProperty.getInstance(), type.getProperties());
		Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), type.getProperties());
		// we have an array, this is represented by a type that extends another type
		// we don't put arrays on the root? could be too broad an assumption... (@03-04-2017)
		if (!isRoot && maxOccurs != null && maxOccurs != 1) {
			Type superType = type.getSuperType();
			content.put("type", "array");
			if (maxOccurs != 0) {
				content.put("maxItems", maxOccurs);
			}
			content.put("minItems", minOccurs == null ? 1 : minOccurs);
			// if it is a not a type that is defined in this definition, unfold it internally
			if (definition == null || !definition.getId().equals(superType.getNamespace())) {
				content.put("items", formatDefinedType(definition, superType, false));
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
				Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
				for (Element<?> child : expandInline ? TypeUtils.getAllChildren((ComplexType) type) : (ComplexType) type) {
					Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
					String name = child.getName();
					boolean isAttribute = false;
					if (name.startsWith("@")) {
						name = name.substring(1);
						isAttribute = true;
					}
					Map<String, Object> childProperties = formatElement(definition, child, true, false);
					Value<String> dynamicName = child.getProperty(DynamicNameProperty.getInstance());
					if (dynamicName != null && dynamicName.getValue() != null && child.getType().isList(child.getProperties()) && childProperties.get("items") != null) {
						Map<String, Object> itemsMap = (Map<String, Object>) childProperties.get("items");
						// remove the dynamic field itself, from both the required and the properties
						List<String> itemsRequired = (List<String>) itemsMap.get("required");
						if (itemsRequired != null) {
							itemsRequired.remove(dynamicName.getValue());
						}
						Map<String, Object> itemsPropertiesMap = (Map<String, Object>) itemsMap.get("properties");
						if (itemsPropertiesMap != null) {
							itemsPropertiesMap.remove(dynamicName.getValue());
						}
						additionalProperties.putAll(itemsMap);
					}
					else {
						properties.put(name, childProperties);
						if (property == null || property.getValue() != 0) {
							required.add(name);
						}
					}
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
				if (!additionalProperties.isEmpty()) {
					content.put("additionalProperties", additionalProperties);
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
					Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();
					for (Element<?> child : TypeUtils.getAllChildren((ComplexType) element.getType())) {
						Value<Integer> property = child.getProperty(MinOccursProperty.getInstance());
						String name = child.getName();
						boolean isAttribute = false;
						if (name.startsWith("@")) {
							name = name.substring(1);
							isAttribute = true;
						}
						Map<String, Object> childProperties = formatElement(definition, child, true, false);
						Value<String> dynamicName = child.getProperty(DynamicNameProperty.getInstance());
						// only supported if it is a list!
						if (dynamicName != null && dynamicName.getValue() != null && child.getType().isList(child.getProperties()) && childProperties.get("items") != null) {
							Map<String, Object> itemsMap = (Map<String, Object>) childProperties.get("items");
							// remove the dynamic field itself, from both the required and the properties
							List<String> itemsRequired = (List<String>) itemsMap.get("required");
							if (itemsRequired != null) {
								itemsRequired.remove(dynamicName.getValue());
							}
							Map<String, Object> itemsPropertiesMap = (Map<String, Object>) itemsMap.get("properties");
							if (itemsPropertiesMap != null) {
								itemsPropertiesMap.remove(dynamicName.getValue());
							}
							additionalProperties.putAll(itemsMap);
						}
						else {
							properties.put(name, childProperties);
							if (property == null || property.getValue() != 0) {
								required.add(name);
							}
						}
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
					if (!additionalProperties.isEmpty()) {
						content.put("additionalProperties", additionalProperties);
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
		
		Converter converter = ConverterFactory.getInstance().getConverter();
		Object maxExclusive = ValueUtils.getValue(new MaxExclusiveProperty(), properties);
		
		// the min/max inclusive/exclusive can have broader meanings (e.g. Date) which can not be represented in swagger so don't try
		if (maxExclusive != null) {
			if (converter.canConvert(maxExclusive.getClass(), Double.class)) {
				content.put("maximum", converter.convert(maxExclusive, Double.class));
				content.put("exclusiveMaximum", true);
			}
		}
		else {
			Object maxInclusive = ValueUtils.getValue(new MaxInclusiveProperty(), properties);
			if (maxInclusive != null) {
				if (converter.canConvert(maxInclusive.getClass(), Double.class)) {
					content.put("maximum", converter.convert(maxInclusive, Double.class));
					content.put("exclusiveMaximum", false);
				}
			}
		}
		
		Object minExclusive = ValueUtils.getValue(new MinExclusiveProperty(), properties);
		if (minExclusive != null) {
			if (converter.canConvert(minExclusive.getClass(), Double.class)) {
				content.put("minimum", converter.convert(minExclusive, Double.class));
				content.put("exclusiveMinimum", true);
			}
		}
		else {
			Object minInclusive = ValueUtils.getValue(new MinInclusiveProperty(), properties);
			if (minInclusive != null) {
				if (converter.canConvert(minInclusive.getClass(), Double.class)) {
					content.put("minimum", converter.convert(minInclusive, Double.class));
					content.put("exclusiveMinimum", false);
				}
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
		
		// there is no support in swagger for exact length
		Integer exactLength = ValueUtils.getValue(LengthProperty.getInstance(), properties);
		if (exactLength != null) {
			content.put("minLength", exactLength);
			content.put("maxLength", exactLength);
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
			String customFormat = null;
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
				if (format != null) {
					Granularity granularity = DateUtils.getGranularity(format);
					switch (granularity) {
						case DATE:
							subType = ParameterSubType.DATE;
						break;
						case TIMESTAMP:
							subType = ParameterSubType.DATE_TIME;
						break;
						case TIME:
							if (allowCustomFormats) {
								customFormat = "time";
							}
							else {
								subType = ParameterSubType.DATE_TIME;	
							}
						break;
					}
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
			else if (BigInteger.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.INTEGER;
			}
			else if (Float.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.NUMBER;
				subType = ParameterSubType.FLOAT;
			}
			else if (Double.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.NUMBER;
				subType = ParameterSubType.DOUBLE;
			}
			else if (BigDecimal.class.isAssignableFrom(instanceClass)) {
				mainType = ParameterType.NUMBER;
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
			else if (allowCustomFormats) {
				if (URI.class.isAssignableFrom(instanceClass)) {
					content.put("format", "uri");
				}
				else if (UUID.class.isAssignableFrom(instanceClass)) {
					content.put("format", "uuid");
				}
				else if (BigDecimal.class.isAssignableFrom(instanceClass)) {
					content.put("format", "bigDecimal");
				}
				else if (BigInteger.class.isAssignableFrom(instanceClass)) {
					content.put("format", "bigInteger");
				}
				else if (customFormat != null) {
					content.put("format", customFormat);
				}
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

	public boolean isAllowCustomFormats() {
		return allowCustomFormats;
	}

	public void setAllowCustomFormats(boolean allowCustomFormats) {
		this.allowCustomFormats = allowCustomFormats;
	}

	public boolean isIncludeDocumentation() {
		return includeDocumentation;
	}

	public void setIncludeDocumentation(boolean includeDocumentation) {
		this.includeDocumentation = includeDocumentation;
	}

}
