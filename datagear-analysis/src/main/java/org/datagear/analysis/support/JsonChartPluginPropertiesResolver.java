/*
 * Copyright (c) 2018 datagear.tech. All Rights Reserved.
 */

/**
 * 
 */
package org.datagear.analysis.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.datagear.analysis.Category;
import org.datagear.analysis.ChartPlugin;
import org.datagear.analysis.ChartProperty;
import org.datagear.analysis.DataSign;
import org.datagear.analysis.Icon;
import org.datagear.analysis.PropertyType;
import org.datagear.analysis.RenderStyle;
import org.datagear.analysis.constraint.Constraint;
import org.datagear.analysis.constraint.Max;
import org.datagear.analysis.constraint.MaxLength;
import org.datagear.analysis.constraint.Min;
import org.datagear.analysis.constraint.MinLength;
import org.datagear.analysis.constraint.Required;
import org.datagear.util.IOUtil;
import org.datagear.util.i18n.Label;

/**
 * JSON {@linkplain ChartPlugin}属性解析器。
 * <p>
 * 此类从JSON解析{@linkplain ChartPlugin}对象的属性：
 * </p>
 * <code>
 * <pre>
 * {
 * 	id : "...",
 * 	nameLabel : "..." 或者 { value : "...", localeValues : { "zh" : "...", "en" : "..." }},
 * 	descLabel : "..." 或者 { ... },
 * 	manualLabel : "..." 或者 { ... },
 * 	icons : "..." 或者 { "LIGHT" : "icons/light.png", "DARK" : "icons/dark.png" },
 * 	chartProperties :  [ { ... }, ... ],
 * 	dataSigns : [ { ... }, ... ],
 * 	version : "...",
 * 	order: 1,
 * 	category: "..." 或者 {name: "...", ...}
 * }
 * </pre>
 * </code>
 * <p>
 * 此类是线程安全的。
 * </p>
 * 
 * @author datagear@163.com
 *
 */
public class JsonChartPluginPropertiesResolver
{
	private PropertyTypeValueConverter propertyTypeValueConverter = new PropertyTypeValueConverter();

	private ConcurrentMap<String, Locale> _localeCache = new ConcurrentHashMap<>();

	public JsonChartPluginPropertiesResolver()
	{
		super();
	}

	public PropertyTypeValueConverter getPropertyTypeValueConverter()
	{
		return propertyTypeValueConverter;
	}

	public void setPropertyTypeValueConverter(PropertyTypeValueConverter propertyTypeValueConverter)
	{
		this.propertyTypeValueConverter = propertyTypeValueConverter;
	}

	/**
	 * 从映射表解析并设置{@linkplain ChartPlugin}属性。
	 * <p>
	 * 它会进行必要的类型转换。
	 * </p>
	 * 
	 * @param chartPlugin
	 * @param properties
	 */
	public void resolveChartPluginProperties(AbstractChartPlugin<?> chartPlugin, Map<String, ?> properties)
	{
		chartPlugin.setId((String) properties.get(ChartPlugin.PROPERTY_ID));
		chartPlugin.setNameLabel(convertToLabel(properties.get(ChartPlugin.PROPERTY_NAME_LABEL)));
		chartPlugin.setDescLabel(convertToLabel(properties.get(ChartPlugin.PROPERTY_DESC_LABEL)));
		chartPlugin.setManualLabel(convertToLabel(properties.get(ChartPlugin.PROPERTY_MANUAL_LABEL)));
		chartPlugin.setIcons(convertToIcons(properties.get(ChartPlugin.PROPERTY_ICONS)));
		chartPlugin.setChartProperties(convertToChartProperties(properties.get(ChartPlugin.PROPERTY_CHART_PROPERTIES)));
		chartPlugin.setDataSigns(convertToDataSigns(properties.get(ChartPlugin.PROPERTY_DATA_SIGNS)));
		chartPlugin.setVersion((String) properties.get(ChartPlugin.PROPERTY_VERSION));
		chartPlugin.setOrder(convertToInt(properties.get(ChartPlugin.PROPERTY_ORDER), chartPlugin.getOrder()));
		chartPlugin.setCategory(convertToCategory(properties.get(ChartPlugin.PROPERTY_CATEGORY)));
	}

	/**
	 * 从JSON字符串解析并设置{@linkplain ChartPlugin}属性。
	 * 
	 * @param chartPlugin
	 * @param json
	 * @throws IOException
	 */
	public void resolveChartPluginProperties(AbstractChartPlugin<?> chartPlugin, String json) throws IOException
	{
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = JsonSupport.parseNonStardand(json, Map.class);
		resolveChartPluginProperties(chartPlugin, properties);
	}

	/**
	 * 从JSON输入流解析并设置{@linkplain ChartPlugin}属性。
	 * 
	 * @param chartPlugin
	 * @param jsonReader
	 * @throws IOException
	 */
	public void resolveChartPluginProperties(AbstractChartPlugin<?> chartPlugin, Reader jsonReader) throws IOException
	{
		String json = null;

		StringWriter writer = null;
		try
		{
			writer = new StringWriter();
			IOUtil.write(jsonReader, writer);
		}
		finally
		{
			IOUtil.close(writer);
		}

		json = writer.toString();

		resolveChartPluginProperties(chartPlugin, json);
	}

	/**
	 * 从JSON输入流解析并设置{@linkplain ChartPlugin}属性。
	 * 
	 * @param chartPlugin
	 * @param in
	 * @param encoding
	 * @throws IOException
	 */
	public void resolveChartPluginProperties(AbstractChartPlugin<?> chartPlugin, InputStream in, String encoding)
			throws IOException
	{
		Reader reader = IOUtil.getReader(in, encoding);
		resolveChartPluginProperties(chartPlugin, reader);
	}

	/**
	 * 将对象转换为{@linkplain Label}。
	 * 
	 * @param obj
	 * @return
	 */
	protected Label convertToLabel(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Label)
			return (Label) obj;
		else if (obj instanceof String)
		{
			Label label = createLabel();
			label.setValue((String) obj);

			return label;
		}
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, ?>) obj;

			Label label = createLabel();
			label.setValue((String) map.get(Label.PROPERTY_VALUE));

			Object localeValuesObj = map.get(Label.PROPERTY_LOCALE_VALUES);
			if (localeValuesObj != null)
			{
				Map<Locale, String> localeValues = new HashMap<>();

				@SuppressWarnings("unchecked")
				Map<String, String> stringLocaleValues = (Map<String, String>) localeValuesObj;

				for (Map.Entry<String, String> entry : stringLocaleValues.entrySet())
				{
					Locale locale = this._localeCache.get(entry.getKey());
					if (locale == null)
					{
						locale = stringToLocale(entry.getKey());
						this._localeCache.putIfAbsent(entry.getKey(), locale);
					}

					localeValues.put(locale, entry.getValue());
				}

				label.setLocaleValues(localeValues);
			}

			return label;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ Label.class.getName() + "] is not supported");
	}

	/**
	 * 将对象转换为{@linkplain Icon}映射表。
	 * 
	 * @param obj
	 * @return
	 */
	protected Map<RenderStyle, Icon> convertToIcons(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof String)
		{
			Map<RenderStyle, Icon> icons = new HashMap<>();
			icons.put(RenderStyle.LIGHT, convertToIcon(obj));

			return icons;
		}
		else if (obj instanceof Map<?, ?>)
		{
			Map<RenderStyle, Icon> icons = new HashMap<>();

			Map<?, ?> map = (Map<?, ?>) obj;

			for (Map.Entry<?, ?> entry : map.entrySet())
			{
				Object key = entry.getKey();
				RenderStyle renderStyle = convertToRenderStyle(key);

				if (renderStyle == null)
					continue;

				Icon icon = convertToIcon(entry.getValue());

				icons.put(renderStyle, icon);
			}

			return icons;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ Icon.class.getName() + "] map is not supported");
	}

	/**
	 * 将对象转换为{@linkplain Icon}。
	 * 
	 * @param obj
	 * @return
	 */
	protected Icon convertToIcon(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Icon)
			return (Icon) obj;
		else if (obj instanceof String)
		{
			LocationIcon icon = createLocationIcon();
			icon.setLocation((String) obj);

			return icon;
		}
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, String>) obj;

			String location = (String) map.get(LocationIcon.PROPERTY_LOCATION);

			if (location == null)
				return null;

			LocationIcon icon = createLocationIcon();
			icon.setLocation(location);

			return icon;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ Icon.class.getName() + "] is not supported");
	}

	/**
	 * 将对象转换为{@linkplain RenderStyle}。
	 * 
	 * @param obj
	 * @return
	 */
	protected RenderStyle convertToRenderStyle(Object obj)
	{
		return convertToEnum(obj, RenderStyle.class);
	}

	/**
	 * 将对象转换为{@linkplain ChartProperties}。
	 * 
	 * @param obj
	 * @return
	 */
	protected List<ChartProperty> convertToChartProperties(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Object[])
		{
			Object[] array = (Object[]) obj;

			List<ChartProperty> chartProperties = new ArrayList<>();

			for (Object ele : array)
			{
				ChartProperty chartProperty = convertToChartProperty(ele);

				if (chartProperty != null)
					chartProperties.add(chartProperty);
			}

			if (chartProperties.isEmpty())
				return null;

			return chartProperties;
		}
		else if (obj instanceof Collection<?>)
		{
			Collection<?> collection = (Collection<?>) obj;
			Object[] array = new Object[collection.size()];
			collection.toArray(array);

			return convertToChartProperties(array);
		}
		else
		{
			Object[] array = new Object[] { obj };

			return convertToChartProperties(array);
		}
	}

	/**
	 * 将对象转换为{@linkplain ChartProperty}。
	 * 
	 * @param obj
	 * @return
	 */
	protected ChartProperty convertToChartProperty(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof ChartProperty)
			return (ChartProperty) obj;
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, ?>) obj;

			String name = (String) map.get(ChartProperty.PROPERTY_NAME);
			if (name == null || name.isEmpty())
				return null;

			ChartProperty chartProperty = createChartProperty();
			chartProperty.setName(name);

			PropertyType type = convertToPropertyType(map.get(ChartProperty.PROPERTY_TYPE));
			if (type == null)
				type = PropertyType.STRING;

			chartProperty.setType(type);
			chartProperty.setNameLabel(convertToLabel(map.get(ChartProperty.PROPERTY_NAME_LABEL)));
			chartProperty.setDescLabel(convertToLabel(map.get(ChartProperty.PROPERTY_DESC_LABEL)));
			chartProperty
					.setDefaultValue(convertToPropertyTypeValue(type, map.get(ChartProperty.PROPERTY_DEFAULT_VALUE)));
			chartProperty.setConstraints(convertToConstraints(map.get(ChartProperty.PROPERTY_CONSTRAINTS)));

			return chartProperty;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ ChartProperty.class.getName() + "] is not supported");
	}

	/**
	 * 将对象转换为{@linkplain PropertyType}。
	 * 
	 * @param obj
	 * @return
	 */
	protected PropertyType convertToPropertyType(Object obj)
	{
		return convertToEnum(obj, PropertyType.class);
	}

	/**
	 * 将对象转换为{@linkplain PropertyType}所描述类型的值。
	 * 
	 * @param propertyType
	 * @param obj
	 * @return
	 */
	protected Object convertToPropertyTypeValue(PropertyType propertyType, Object obj)
	{
		return this.propertyTypeValueConverter.convert(propertyType, obj);
	}

	/**
	 * 将对象转换为{@linkplain Constraint}集合。
	 * 
	 * @param obj
	 * @return
	 */
	protected Set<Constraint> convertToConstraints(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, ?>) obj;

			Set<Constraint> constraints = new HashSet<>();

			for (Map.Entry<String, ?> entry : map.entrySet())
			{
				Constraint constraint = convertToConstraint(entry.getKey(), entry.getValue());

				if (constraint != null)
					constraints.add(constraint);
			}

			return constraints;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ Constraint.class.getName() + "] set is not supported");
	}

	/**
	 * 将对象转换为{@linkplain Constraint}，不支持则返回{@code null}。
	 * 
	 * @param name
	 * @param value
	 * @return
	 */
	protected Constraint convertToConstraint(String name, Object value)
	{
		if (value == null)
			return null;
		else if (Max.class.getSimpleName().equalsIgnoreCase(name))
		{
			Number v = (Number) this.propertyTypeValueConverter.convert(PropertyType.NUMBER, value);
			return new Max(v);
		}
		else if (MaxLength.class.getSimpleName().equalsIgnoreCase(name))
		{
			Number v = (Number) this.propertyTypeValueConverter.convert(PropertyType.NUMBER, value);
			return new MaxLength(v.intValue());
		}
		else if (Min.class.getSimpleName().equalsIgnoreCase(name))
		{
			Number v = (Number) this.propertyTypeValueConverter.convert(PropertyType.NUMBER, value);
			return new Min(v);
		}
		else if (MinLength.class.getSimpleName().equalsIgnoreCase(name))
		{
			Number v = (Number) this.propertyTypeValueConverter.convert(PropertyType.NUMBER, value);
			return new MinLength(v.intValue());
		}
		else if (Required.class.getSimpleName().equalsIgnoreCase(name))
		{
			Boolean v = (Boolean) this.propertyTypeValueConverter.convert(PropertyType.BOOLEAN, value);
			return new Required(v);
		}
		else
			return null;
	}

	/**
	 * 将对象转换为指定枚举类型的对象。
	 * 
	 * @param obj
	 * @param enumType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T extends Enum<T>> T convertToEnum(Object obj, Class<T> enumType)
	{
		if (obj == null)
			return null;
		else if (enumType.isAssignableFrom(obj.getClass()))
			return (T) obj;
		else if (obj instanceof String)
		{
			String strVal = (String) obj;

			EnumSet<T> enumSet = EnumSet.allOf(enumType);

			for (T e : enumSet)
			{
				if (e.name().equalsIgnoreCase(strVal))
					return e;
			}

			return null;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ enumType.getName() + "] is not supported");
	}

	/**
	 * 将对象转换为{@linkplain DataSigns}。
	 * 
	 * @param obj
	 * @return
	 */
	protected List<DataSign> convertToDataSigns(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Object[])
		{
			Object[] array = (Object[]) obj;

			List<DataSign> dataSigns = new ArrayList<>();

			for (Object ele : array)
			{
				DataSign dataSign = convertToDataSign(ele);

				if (dataSign != null)
					dataSigns.add(dataSign);
			}

			if (dataSigns.isEmpty())
				return null;

			return dataSigns;
		}
		else if (obj instanceof Collection<?>)
		{
			Collection<?> collection = (Collection<?>) obj;
			Object[] array = new Object[collection.size()];
			collection.toArray(array);

			return convertToDataSigns(array);
		}
		else
		{
			Object[] array = new Object[] { obj };

			return convertToDataSigns(array);
		}
	}

	/**
	 * 将对象转换为{@linkplain DataSign}。
	 * 
	 * @param obj
	 * @return
	 */
	protected DataSign convertToDataSign(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof DataSign)
			return (DataSign) obj;
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, ?>) obj;

			String name = (String) map.get(DataSign.PROPERTY_NAME);
			if (name == null || name.isEmpty())
				return null;

			DataSign dataSign = createDataSign();
			dataSign.setName(name);

			dataSign.setRequired(convertToBoolean(map.get(DataSign.PROPERTY_REQUIRED), true));
			dataSign.setMultiple(convertToBoolean(map.get(DataSign.PROPERTY_MULTIPLE), true));
			dataSign.setNameLabel(convertToLabel(map.get(DataSign.PROPERTY_NAME_LABEL)));
			dataSign.setDescLabel(convertToLabel(map.get(DataSign.PROPERTY_DESC_LABEL)));

			return dataSign;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ DataSign.class.getName() + "] is not supported");
	}

	protected Category convertToCategory(Object obj)
	{
		if (obj == null)
			return null;
		else if (obj instanceof Category)
			return (Category) obj;
		else if (obj instanceof String)
			return new Category((String) obj);
		else if (obj instanceof Map<?, ?>)
		{
			@SuppressWarnings("unchecked")
			Map<String, ?> map = (Map<String, ?>) obj;

			String name = (String) map.get(DataSign.PROPERTY_NAME);
			if (name == null)
				return null;

			Category category = createCategory();
			category.setName(name);

			category.setNameLabel(convertToLabel(map.get(Category.PROPERTY_NAME_LABEL)));
			category.setDescLabel(convertToLabel(map.get(Category.PROPERTY_DESC_LABEL)));
			category.setOrder(convertToInt(map.get(Category.PROPERTY_ORDER), category.getOrder()));

			return category;
		}
		else
			throw new UnsupportedOperationException("Convert object of type [" + obj.getClass().getName() + "] to ["
					+ Category.class.getName() + "] is not supported");
	}

	/**
	 * 将对象转换为布尔值。
	 * 
	 * @param obj
	 * @param defaultValue
	 * @return
	 */
	protected boolean convertToBoolean(Object obj, boolean defaultValue)
	{
		if (obj == null)
			return defaultValue;
		else if (obj instanceof Boolean)
			return ((Boolean) obj).booleanValue();
		else if (obj instanceof String)
		{
			String str = (String) obj;
			return ("1".equals(str) || "true".equalsIgnoreCase(str));
		}
		else
			throw new UnsupportedOperationException(
					"Convert object [" + obj + "] to [" + boolean.class.getName() + "] is not supported");
	}

	protected int convertToInt(Object obj, int defaultValue)
	{
		if (obj == null)
			return defaultValue;
		else if (obj instanceof Number)
			return ((Number) obj).intValue();
		else if (obj instanceof String)
		{
			try
			{
				return Integer.parseInt((String) obj);
			}
			catch (Exception e)
			{
				return defaultValue;
			}
		}
		else
			throw new UnsupportedOperationException(
					"Convert object [" + obj + "] to [" + Integer.class.getName() + "] is not supported");
	}

	/**
	 * 字符串转换为{@linkplain Locale}。
	 * 
	 * @param str
	 * @return
	 */
	protected Locale stringToLocale(String str)
	{
		return Label.toLocale(str);
	}

	protected Label createLabel()
	{
		return new Label();
	}

	protected LocationIcon createLocationIcon()
	{
		return new LocationIcon();
	}

	protected ChartProperty createChartProperty()
	{
		return new ChartProperty();
	}

	protected DataSign createDataSign()
	{
		return new DataSign();
	}

	protected Category createCategory()
	{
		return new Category();
	}
}
