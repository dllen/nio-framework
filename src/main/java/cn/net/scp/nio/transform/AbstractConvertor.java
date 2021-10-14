package cn.net.scp.nio.transform;

public abstract class AbstractConvertor<I, O> extends AbstractForwarder<I, O> {

    public abstract O convert(I input) throws ConvertException;

}
