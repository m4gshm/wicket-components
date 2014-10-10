package ru.buls.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.*;
import org.apache.wicket.markup.html.WebMarkupContainerWithAssociatedMarkup;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.parser.XmlTag;
import org.apache.wicket.markup.parser.filter.WicketTagIdentifier;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;

import static org.apache.wicket.markup.MarkupResourceData.NO_MARKUP_RESOURCE_DATA;


/**
 * Created by alexander on 05.10.14.
 * Предназначен для генерации разметки форм на основе шаблона
 * Пример шаблона
 *  <span wicket:id="fields">
 *        <wicket:label/> <wicket:field class="cssClass"/><br/>
 *  </span>
 *  где атрибут wicket:id="fields" - идентификатор компонента
 *  <wicket:label/> - подпись элемента формы
 *  <wicket:field/> - элемент формы
 *
 *  Комноненты добавляются методом add(Component child) или add(Component child, boolean enclosureVisible)
 *  @see ChildTagBuilder - реализация генерации тегов для различных элементов формы
 */
public class FieldsRepeater extends MarkupContainer {

    private static final String WICKET_ID = "wicket:id";
    private static final String WICKET_FIELD = "wicket:field";
    private static final String WICKET_FOR = "wicket:for";
    private static final String LABEL = "label";
    private Logger logger = LoggerFactory.getLogger(FieldsRepeater.class);

    static {
        WicketTagIdentifier.registerWellKnownTagName("field");
        WicketTagIdentifier.registerWellKnownTagName("label");
    }

    protected Decorator<String> labelDecorator = new LabelDecorator();
    protected ChildTagBuilder childTagBuilder = new ChildTagBuilder();
    protected boolean simplifyMarkupId = true;

    private String generatedMarkup;

    public FieldsRepeater(String id) {
        super(id);
    }


    public Enclosure add(Component child) {
        return add(child, true);
    }

    public Enclosure add(Component child, boolean enclosureVisible) {
        Enclosure enclo = new Enclosure(getEnclosureId(child));
        enclo.add(child);
        enclo.setVisible(enclosureVisible);
        if (simplifyMarkupId) {
            enclo.setOutputMarkupId(getOutputMarkupId());
            enclo.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setOutputMarkupPlaceholderTag(getOutputMarkupPlaceholderTag());
            child.setMarkupId(child.getId());
            enclo.setMarkupId(enclo.getId());
        }
        super.add(enclo);
        return enclo;
    }

    @Override
    public MarkupStream getAssociatedMarkupStream(boolean throwException) {

        if (generatedMarkup == null) {
            StringBuilder builder = new StringBuilder();

            MarkupStream markupStream = getMarkupStream();

            int startIndex = markupStream.getCurrentIndex();

            for (int i = 0; i < size(); ++i) {
                markupStream.setCurrentIndex(startIndex);
                Component component = get(i);
                Markup markup = generate((Enclosure) component, markupStream);
                toBuilder(markup, builder);
            }

            generatedMarkup = builder.toString();
        }
        Markup markup;
        try {
            Markup parse = new MarkupParser(generatedMarkup).parse();
            copy(parse, markup = new Markup(NO_MARKUP_RESOURCE_DATA));
        } catch (IOException e) {
            logger.error("error on parsing generated markup : " + generatedMarkup, e);
            throw new RuntimeException(e);
        } catch (ResourceStreamNotFoundException e) {
            logger.error("error on parsing generated markup : " + generatedMarkup, e);
            throw new RuntimeException(e);
        }
        return new MarkupStream(markup);
    }

    protected void toBuilder(Markup markup, StringBuilder builder) {
        for (int j = 0; j < markup.size(); ++j) {
            MarkupElement element = markup.get(j);
            if (element != null) builder.append(String.valueOf(element.toCharSequence()).trim());
        }
    }

    protected Markup generate(Enclosure enclosure, MarkupStream markupStream) {
        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);

        ComponentTag startTag = markupStream.getTag();
        assert startTag != null;
        assert !startTag.isClose();

        openEnclosure(markup, startTag, enclosure);
        Component child = enclosure.get(0);
        int endIndex = child(markupStream, markup, startTag, child);
        closeEnclosure(endIndex, markup, markupStream);

//        if (endIndex > 0) markupStream.setCurrentIndex(endIndex);
//        if (markupStream.hasMore()) markupStream.next();
        return markup;
    }

    private void closeEnclosure(int endIndex, Markup markup, MarkupStream markupStream) {
        MarkupElement closeTag = markupStream.get(endIndex);
        markup.addMarkupElement(closeTag);
    }

    private int child(MarkupStream markupStream, Markup markup, ComponentTag baseTag, Component child) {
        int endIndex = -1;
        MarkupElement next;
        ComponentTag startTag = (ComponentTag) markupStream.get();
        while (null != (next = markupStream.next())) {
            if (next instanceof ComponentTag) {
                ComponentTag cnext = (ComponentTag) next;
                ComponentTag ot = cnext.getOpenTag();
                if (ot != null && ot.equals(startTag)) {
                    endIndex = markupStream.getCurrentIndex();
                    break;
                }
                assert ot == null || !baseTag.getId().equals(ot.getId()) : "markup overflow";
            }
            copy(createMarkupFor(next, child), markup);
        }
        return endIndex;
    }

    private void openEnclosure(Markup markup, ComponentTag startTag, Enclosure enclosure) {
        ComponentTag tag = new ComponentTag(startTag.getName(), startTag.getType());
        tag.putAll(startTag.getAttributes());
        tag.put("wicket:id", enclosure.getMarkupId());
        markup.addMarkupElement(tag);
    }


    @Override
    public boolean hasAssociatedMarkup() {
        return true;
    }

    @Override
    protected void onRender(@Deprecated final MarkupStream markupStream) {
        int startIndex = markupStream.getCurrentIndex();
        MarkupElement thisElement = markupStream.get();
        if (thisElement == null) {
            throw new IllegalArgumentException("markupStream.get() return null");
        } else if (!(thisElement instanceof ComponentTag)) {
            throw new IllegalArgumentException("markupStream.get() must return ComponentTag");
        }

        //рендерим чайлды на основе своего маркапа
        MarkupStream stream = getAssociatedMarkupStream(false);
        while (stream.hasMore()) {
            int currentIndex = stream.getCurrentIndex();

            MarkupElement markupElement = stream.get();
            if (markupElement instanceof ComponentTag) {
                ComponentTag coTag = (ComponentTag) markupElement;
                String id = coTag.getId();
                Component child = get(id);
                child.render(stream);
                int index = stream.getCurrentIndex();
                assert !(index < currentIndex);
                if (index == currentIndex) {
                    stream.setCurrentIndex(currentIndex + 1);
                }
            }
        }

        int endIndex = markupStream.getCurrentIndex();
        //по текущему маркапу проходим до конца, имитируя рендеринг
        ComponentTag openTag = (ComponentTag) thisElement;

        MarkupElement next = markupStream.get();
        while (next != null) {
            if (next instanceof ComponentTag) {
                ComponentTag closeTag = (ComponentTag) next;
                if (openTag.equals(closeTag.getOpenTag())) {
                    //if(markupStream.hasMore()) markupStream.next();
                    break;
                }
            }
            next = markupStream.next();
        }

//        markupStream.next();

//        int startIndex = markupStream.getCurrentIndex();
//
//        for (int i = 0; i < size(); ++i) {
//            markupStream.setCurrentIndex(startIndex);
//            Component component = get(i);
//            component.render(markupStream);
//        }

    }


    private void copy(Markup from, Markup to) {
        for (int i = 0; i < from.size(); ++i) {
            to.addMarkupElement(from.get(i));
        }
    }

    private String getEnclosureId(Component child) {
        return "enclosureFor" + child.getMarkupId();
    }

    private Markup createMarkupFor(MarkupElement tag, Component child) {
        Markup childMarkup;
        if (tag instanceof WicketTag) {
            WicketTag wtag = (WicketTag) tag;
            String name = wtag.getName();
            if (!wtag.isOpenClose()) {
                throw new IllegalStateException(tag + " must be closed");
            }
            if ("field".equals(name))
                childMarkup = createChildMarkup(child, wtag);
            else if (LABEL.equals(name))
                childMarkup = getLabelMarkup(child);
            else throw new IllegalStateException(tag.toString());
        } else if (tag instanceof ComponentTag) {
            ComponentTag cTag = (ComponentTag) tag;
            if ((cTag.isOpen() || cTag.isOpenClose()) && LABEL.equals(cTag.getName())) {
                String wicketFor = cTag.getAttribute(WICKET_FOR);
                if (WICKET_FIELD.equals(wicketFor)) {
                    ComponentTag newTag = new ComponentTag(cTag.getName(), cTag.getType());
                    newTag.getAttributes().putAll(cTag.getAttributes());
                    cTag = newTag;
                    //cTag.getAttributes().remove("wicket:for");
                    cTag.getAttributes().put(WICKET_FOR, child.getMarkupId());
                } else if (wicketFor != null) {
                    throw new IllegalStateException("incorrect value '" + wicketFor
                            + "' for attribute " + WICKET_FIELD + " of tag " + cTag);
                }
            }

            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
            markup.addMarkupElement(cTag);

            childMarkup = markup;
        } else {
            Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
            markup.addMarkupElement(tag);
            childMarkup = markup;
        }
        return childMarkup;
    }

    private Markup createChildMarkup(Component child, WicketTag wtag) {
        String tagName = childTagBuilder.getTagName(child);
        ComponentTag open = childTagBuilder.createOpenTag(child, tagName);

        String childId = child.getMarkupId();
        open.setId(childId);
        open.put(WICKET_ID, childId);

        //копируем атрибуты викет тега
        if (wtag != null)
            open.getAttributes().putAll(wtag.getAttributes());

        ComponentTag close = childTagBuilder.createCloseTag(tagName, open);
        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
        if (open instanceof ComponentTag) {
            markup.addMarkupElement(open);
            if (close != null) markup.addMarkupElement(close);
        }
        return markup;
    }

    private Markup getLabelMarkup(Component child) {
        String label = labelDecorator.decorate(getLabel(child));

        Markup markup = new Markup(NO_MARKUP_RESOURCE_DATA);
        markup.addMarkupElement(new RawMarkup(label));

        return markup;
    }

    protected String getLabel(Component child) {
        String label = child.getMarkupId();
        if (child instanceof ILabelProvider) {
            ILabelProvider labelProvider = (ILabelProvider) child;

            IModel model = labelProvider.getLabel();
            label = model != null ? model.toString() : label;
        }
        return label;
    }

    static class LabelDecorator implements Decorator<String> {
        @Override
        public String decorate(String s) {
            return s;
        }
    }

    class ChildTagBuilder implements Serializable {
        private String getTagName(Component child) {
            String tagName;
            if (child instanceof TextArea) tagName = "textarea";
            else if (child instanceof TextField) tagName = "input";
            else if (child instanceof CheckBox) tagName = "input";
            else if (child instanceof AbstractChoice) tagName = "select";
            else if (child instanceof WebMarkupContainerWithAssociatedMarkup || child instanceof FormComponentPanel)
                tagName = "span";
            else if (child instanceof FieldsRepeater) tagName = "span";
            else {
                throw new UnsupportedOperationException("does not support child element " + child);
            }
            return tagName;
        }

        protected ComponentTag createCloseTag(String tagName, MarkupElement open) {
            ComponentTag close = new ComponentTag(tagName, XmlTag.CLOSE);
            close.setOpenTag((ComponentTag) open);
            return close;
        }

        protected ComponentTag createOpenTag(Component child, String tagName) {
            ComponentTag open = new ComponentTag(tagName, XmlTag.OPEN);
            if (child instanceof CheckBox) open.getAttributes().put("type", "checkbox");
            if (child instanceof TextField) open.getAttributes().put("type", "text");
            return open;
        }

    }

    class Enclosure extends MarkupContainer {
        public Enclosure(String id) {
            super(id, new Model());
        }

        @Override
        protected void onRender(MarkupStream markupStream) {
            super.onRender(markupStream);
        }
    }
}
