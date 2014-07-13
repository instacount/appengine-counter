/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theupswell.appengine.counter.web.test.jersey;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.Lists;
import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.DataSourceProvider;
import com.sun.jersey.core.impl.provider.entity.FileProvider;
import com.sun.jersey.core.impl.provider.entity.FormMultivaluedMapProvider;
import com.sun.jersey.core.impl.provider.entity.FormProvider;
import com.sun.jersey.core.impl.provider.entity.InputStreamProvider;
import com.sun.jersey.core.impl.provider.entity.MimeMultipartProvider;
import com.sun.jersey.core.impl.provider.entity.ReaderProvider;
import com.sun.jersey.core.impl.provider.entity.RenderedImageProvider;
import com.sun.jersey.core.impl.provider.entity.SourceProvider.StreamSourceReader;
import com.sun.jersey.core.impl.provider.header.CacheControlProvider;
import com.sun.jersey.core.impl.provider.header.CookieProvider;
import com.sun.jersey.core.impl.provider.header.EntityTagProvider;
import com.sun.jersey.core.impl.provider.header.LocaleProvider;
import com.sun.jersey.core.impl.provider.header.MediaTypeProvider;
import com.sun.jersey.core.impl.provider.header.NewCookieProvider;
import com.sun.jersey.core.impl.provider.header.StringProvider;
import com.sun.jersey.core.impl.provider.header.URIProvider;
import com.sun.jersey.server.impl.container.WebApplicationProviderImpl;
import com.sun.jersey.server.impl.container.filter.NormalizeFilter;
import com.sun.jersey.server.impl.container.httpserver.HttpHandlerContainerProvider;
import com.sun.jersey.server.impl.model.method.dispatch.EntityParamDispatchProvider;
import com.sun.jersey.server.impl.model.method.dispatch.FormDispatchProvider;
import com.sun.jersey.server.impl.model.method.dispatch.HttpReqResDispatchProvider;
import com.sun.jersey.server.impl.model.method.dispatch.MultipartFormDispatchProvider;
import com.sun.jersey.server.impl.model.method.dispatch.VoidVoidDispatchProvider;
import com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.DateProvider;
import com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.StringConstructor;
import com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeFromString;
import com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeFromStringEnum;
import com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeValueOf;
import com.sun.jersey.server.impl.provider.RuntimeDelegateImpl;
import com.sun.jersey.server.impl.template.ViewableMessageBodyWriter;
import com.sun.jersey.spi.service.ServiceFinder;

// See https://code.google.com/p/google-guice/issues/detail?id=488
public class QuickServiceIterator<T> extends ServiceFinder.ServiceIteratorProvider<T>
{
	private Logger logger = Logger.getLogger(QuickServiceIterator.class.getName());

	@SuppressWarnings("unchecked")
	@Override
	public Iterator<T> createIterator(Class<T> service, String serviceName, ClassLoader loader,
			boolean ignoreOnClassNotFound)
	{
		logger.entering(this.getClass().getName(), "createIterator", new Object[] {
			service, serviceName, loader
		});

		// ////////////////////////
		// Jersey Server Providers
		// ////////////////////////
		if (com.sun.jersey.spi.container.ContainerRequestFilter.class.isAssignableFrom(service))
		{
			HttpHandlerContainerProvider provider = new com.sun.jersey.server.impl.container.httpserver.HttpHandlerContainerProvider();
			List<T> classList = (List<T>) Lists.newArrayList(provider);
			return classList.iterator();
		}

		if (com.sun.jersey.spi.container.ContainerRequestFilter.class.isAssignableFrom(service))
		{
			NormalizeFilter provider = new com.sun.jersey.server.impl.container.filter.NormalizeFilter();
			List<T> classList = (List<T>) Lists.newArrayList(provider);
			return classList.iterator();
		}

		if (com.sun.jersey.spi.container.ResourceMethodCustomInvokerDispatchProvider.class.isAssignableFrom(service))
		{
			VoidVoidDispatchProvider p1 = new com.sun.jersey.server.impl.model.method.dispatch.VoidVoidDispatchProvider();
			HttpReqResDispatchProvider p2 = new com.sun.jersey.server.impl.model.method.dispatch.HttpReqResDispatchProvider();
			MultipartFormDispatchProvider p3 = new com.sun.jersey.server.impl.model.method.dispatch.MultipartFormDispatchProvider();
			FormDispatchProvider p4 = new com.sun.jersey.server.impl.model.method.dispatch.FormDispatchProvider();
			EntityParamDispatchProvider p5 = new com.sun.jersey.server.impl.model.method.dispatch.EntityParamDispatchProvider();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5);
			return classList.iterator();
		}

		if (com.sun.jersey.spi.container.ResourceMethodDispatchProvider.class.isAssignableFrom(service))
		{
			VoidVoidDispatchProvider p1 = new com.sun.jersey.server.impl.model.method.dispatch.VoidVoidDispatchProvider();
			HttpReqResDispatchProvider p2 = new com.sun.jersey.server.impl.model.method.dispatch.HttpReqResDispatchProvider();
			MultipartFormDispatchProvider p3 = new com.sun.jersey.server.impl.model.method.dispatch.MultipartFormDispatchProvider();
			FormDispatchProvider p4 = new com.sun.jersey.server.impl.model.method.dispatch.FormDispatchProvider();
			EntityParamDispatchProvider p5 = new com.sun.jersey.server.impl.model.method.dispatch.EntityParamDispatchProvider();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5);
			return classList.iterator();
		}

		if (com.sun.jersey.spi.container.WebApplicationProvider.class.isAssignableFrom(service))
		{
			WebApplicationProviderImpl provider = new com.sun.jersey.server.impl.container.WebApplicationProviderImpl();
			List<T> classList = (List<T>) Lists.newArrayList(provider);
			return classList.iterator();
		}

		if (com.sun.jersey.spi.StringReader.class.isAssignableFrom(service))
		{
			TypeFromStringEnum p1 = new com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeFromStringEnum();
			TypeValueOf p2 = new com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeValueOf();
			TypeFromString p3 = new com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.TypeFromString();
			StringConstructor p4 = new com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.StringConstructor();
			DateProvider p5 = new com.sun.jersey.server.impl.model.parameter.multivalued.StringReaderProviders.DateProvider();
			// RootElementProvider p6 = new
			// com.sun.jersey.server.impl.model.parameter.multivalued.JAXBStringReaderProviders.RootElementProvider();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5);
			return classList.iterator();
		}

		if (javax.ws.rs.ext.MessageBodyWriter.class.isAssignableFrom(service))
		{
			ViewableMessageBodyWriter provider = new com.sun.jersey.server.impl.template.ViewableMessageBodyWriter();
			List<T> classList = (List<T>) Lists.newArrayList(provider);
			return classList.iterator();
		}

		if (javax.ws.rs.ext.RuntimeDelegate.class.isAssignableFrom(service))
		{
			RuntimeDelegateImpl provider = new com.sun.jersey.server.impl.provider.RuntimeDelegateImpl();
			List<T> classList = (List<T>) Lists.newArrayList(provider);
			return classList.iterator();
		}

		// ////////////////////////
		// Jersey Core Providers
		// ////////////////////////

		if (com.sun.jersey.spi.HeaderDelegateProvider.class.isAssignableFrom(service))
		{
			LocaleProvider p1 = new com.sun.jersey.core.impl.provider.header.LocaleProvider();
			EntityTagProvider p2 = new com.sun.jersey.core.impl.provider.header.EntityTagProvider();
			MediaTypeProvider p3 = new com.sun.jersey.core.impl.provider.header.MediaTypeProvider();
			CacheControlProvider p4 = new com.sun.jersey.core.impl.provider.header.CacheControlProvider();
			NewCookieProvider p5 = new com.sun.jersey.core.impl.provider.header.NewCookieProvider();
			CookieProvider p6 = new com.sun.jersey.core.impl.provider.header.CookieProvider();
			URIProvider p7 = new com.sun.jersey.core.impl.provider.header.URIProvider();
			com.sun.jersey.core.impl.provider.header.DateProvider p8 = new com.sun.jersey.core.impl.provider.header.DateProvider();
			StringProvider p9 = new com.sun.jersey.core.impl.provider.header.StringProvider();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5, p6, p7, p8, p9);
			return classList.iterator();
		}

		// if
		// (com.sun.jersey.spi.inject.InjectableProvider.class.isAssignableFrom(service))
		// {
		// SAXParserContextProvider p1 = new
		// com.sun.jersey.core.impl.provider.xml.SAXParserContextProvider();
		// XMLStreamReaderContextProvider p2 = new
		// com.sun.jersey.core.impl.provider.xml.XMLStreamReaderContextProvider();
		// DocumentBuilderFactoryProvider p3 = new
		// com.sun.jersey.core.impl.provider.xml.DocumentBuilderFactoryProvider();
		// TransformerFactoryProvider p4 = new
		// com.sun.jersey.core.impl.provider.xml.TransformerFactoryProvider();
		//
		// List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4);
		// return classList.iterator();
		// }

		if (javax.ws.rs.ext.MessageBodyReader.class.isAssignableFrom(service))
		{
			com.sun.jersey.core.impl.provider.entity.StringProvider p1 = new com.sun.jersey.core.impl.provider.entity.StringProvider();
			ByteArrayProvider p2 = new com.sun.jersey.core.impl.provider.entity.ByteArrayProvider();
			FileProvider p3 = new com.sun.jersey.core.impl.provider.entity.FileProvider();
			InputStreamProvider p4 = new com.sun.jersey.core.impl.provider.entity.InputStreamProvider();
			DataSourceProvider p5 = new com.sun.jersey.core.impl.provider.entity.DataSourceProvider();
			RenderedImageProvider p6 = new com.sun.jersey.core.impl.provider.entity.RenderedImageProvider();
			MimeMultipartProvider p7 = new com.sun.jersey.core.impl.provider.entity.MimeMultipartProvider();
			FormProvider p8 = new com.sun.jersey.core.impl.provider.entity.FormProvider();
			FormMultivaluedMapProvider p9 = new com.sun.jersey.core.impl.provider.entity.FormMultivaluedMapProvider();
			// App p10 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.App();
			// Text p11 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.Text();
			// General p12 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.General();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.App
			// p13 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.App();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.Text
			// p14 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.Text();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.General
			// p15 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.General();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.App
			// p16 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.App();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.Text
			// p17 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.Text();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.General
			// p18 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.General();
			ReaderProvider p19 = new com.sun.jersey.core.impl.provider.entity.ReaderProvider();
			// DocumentProvider p20 = new
			// com.sun.jersey.core.impl.provider.entity.DocumentProvider();
			StreamSourceReader p21 = new com.sun.jersey.core.impl.provider.entity.SourceProvider.StreamSourceReader();
			// SAXSourceReader p22 = new
			// com.sun.jersey.core.impl.provider.entity.SourceProvider.SAXSourceReader();
			// DOMSourceReader p23 = new
			// com.sun.jersey.core.impl.provider.entity.SourceProvider.DOMSourceReader();
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.App
			// p24 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.App();
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.Text
			// p25 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.Text();
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.General
			// p26 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootObjectProvider.General();
			// EntityHolderReader p27 = new
			// com.sun.jersey.core.impl.provider.entity.EntityHolderReader();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5, p6, p7, p8, p9, p19, p21);
			return classList.iterator();
		}

		if (javax.ws.rs.ext.MessageBodyWriter.class.isAssignableFrom(service))
		{
			com.sun.jersey.core.impl.provider.entity.StringProvider p1 = new com.sun.jersey.core.impl.provider.entity.StringProvider();
			ByteArrayProvider p2 = new com.sun.jersey.core.impl.provider.entity.ByteArrayProvider();
			FileProvider p3 = new com.sun.jersey.core.impl.provider.entity.FileProvider();
			InputStreamProvider p4 = new com.sun.jersey.core.impl.provider.entity.InputStreamProvider();
			DataSourceProvider p5 = new com.sun.jersey.core.impl.provider.entity.DataSourceProvider();
			RenderedImageProvider p6 = new com.sun.jersey.core.impl.provider.entity.RenderedImageProvider();
			MimeMultipartProvider p7 = new com.sun.jersey.core.impl.provider.entity.MimeMultipartProvider();
			FormProvider p8 = new com.sun.jersey.core.impl.provider.entity.FormProvider();
			FormMultivaluedMapProvider p9 = new com.sun.jersey.core.impl.provider.entity.FormMultivaluedMapProvider();
			// App p10 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.App();
			// Text p11 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.Text();
			// General p12 = new
			// com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider.General();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.App
			// p13 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.App();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.Text
			// p14 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.Text();
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.General
			// p15 = new
			// com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider.General();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.App
			// p16 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.App();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.Text
			// p17 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.Text();
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.General
			// p18 = new
			// com.sun.jersey.core.impl.provider.entity.XMLListElementProvider.General();
			ReaderProvider p19 = new com.sun.jersey.core.impl.provider.entity.ReaderProvider();
			// DocumentProvider p20 = new
			// com.sun.jersey.core.impl.provider.entity.DocumentProvider();
			// StreamingOutputProvider p21 = new
			// com.sun.jersey.core.impl.provider.entity.SourceProvider.StreamingOutputProvider();
			// SAXSourceReader p22 = new
			// com.sun.jersey.core.impl.provider.entity.SourceProvider.SourceWriter();

			List<T> classList = (List<T>) Lists.newArrayList(p1, p2, p3, p4, p5, p6, p7, p8, p9, p19);
			return classList.iterator();
		}

		// //////////////////////////
		// Jersey Servlet
		// //////////////////////////

		// TODO Auto-generated method stub
		return Lists.<T> newArrayList().iterator();
	}

	@Override
	public Iterator<Class<T>> createClassIterator(Class<T> service, String serviceName, ClassLoader loader,
			boolean ignoreOnClassNotFound)
	{
		logger.info("entering createClassIterator");
		// TODO Auto-generated method stub
		return Lists.<Class<T>> newArrayList().iterator();
	}

}
