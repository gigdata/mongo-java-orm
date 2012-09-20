package com.googlecode.mjorm.convert.converters;

import java.util.List;

import org.bson.types.ObjectId;

import com.googlecode.mjorm.MjormException;
import com.googlecode.mjorm.ObjectDescriptor;
import com.googlecode.mjorm.ObjectDescriptorRegistry;
import com.googlecode.mjorm.PropertyDescriptor;
import com.googlecode.mjorm.convert.ConversionContext;
import com.googlecode.mjorm.convert.ConversionException;
import com.googlecode.mjorm.convert.JavaType;
import com.googlecode.mjorm.convert.TypeConverter;
import com.mongodb.BasicDBObject;

public class PojoToMongoTypeConverter
	implements TypeConverter<Object, BasicDBObject> {

	private ObjectDescriptorRegistry registry;

	public PojoToMongoTypeConverter(ObjectDescriptorRegistry registry) {
		this.registry = registry;
	}

	public boolean canConvert(Class<?> sourceClass, Class<?> targetClass) {
		return registry.hasDescriptor(sourceClass)
			&& BasicDBObject.class.equals(targetClass);
	}

	public BasicDBObject convert(
		Object source, JavaType targetType, ConversionContext context)
		throws ConversionException {

		// get source class
		Class<?> sourceClass = source.getClass();

		// get the descriptors
		List<ObjectDescriptor> descriptors = registry.getDescriptorsForType(sourceClass);
		if (descriptors.isEmpty()) {
			throw new MjormException("Unable to find ObjectDescriptor for "+sourceClass.getClass());
		}

		// create the return object
		BasicDBObject ret = new BasicDBObject();

		// loop through each descriptor
		for (ObjectDescriptor descriptor : descriptors) {
	
			// loop through each property
			for (PropertyDescriptor prop : descriptor.getProperties()) {
	
				try {
					// get it
					Object value = prop.get(source);
	
					// handle ids
					if (prop.isIdentifier()) {
						if (value==null && !prop.isAutoGenerated()) {
							continue;
						} else if (value==null && prop.isAutoGenerated()) {
							ObjectId autoGenId = new ObjectId();
							prop.set(source, autoGenId.toStringMongod());
							ret.put("_id", autoGenId);
						} else if (value!=null && ObjectId.class.isInstance(value)) {
							ret.put("_id", ObjectId.class.cast(value));
						} else if (value!=null) {
							ret.put("_id", new ObjectId(String.class.cast(value)));
						}
	
					} else {
						if (value!=null) {
							
							// get storage type
							JavaType storageType = prop.getStorageType();
							if (storageType==null && value!=null) {
								storageType = context.getStorageType(value.getClass());
							}

							// convert
							value = context.convert(value, storageType);
						}
						ret.put(prop.getPropColumn(), value);
					}

				} catch (Exception e) {
					throw new MjormException(
						"Error mapping property "+prop.getName()
						+" of class "+descriptor.getType(), e);
				}
	
			}
		}

		// return it
		return ret;
	}

}
