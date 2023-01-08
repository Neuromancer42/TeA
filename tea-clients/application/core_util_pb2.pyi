from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ApplicationRequest(_message.Message):
    __slots__ = ["alarm_rel", "analysis", "need_rank", "option", "source", "test_suite"]
    class OptionEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    ALARM_REL_FIELD_NUMBER: _ClassVar[int]
    ANALYSIS_FIELD_NUMBER: _ClassVar[int]
    NEED_RANK_FIELD_NUMBER: _ClassVar[int]
    OPTION_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    TEST_SUITE_FIELD_NUMBER: _ClassVar[int]
    alarm_rel: _containers.RepeatedScalarFieldContainer[str]
    analysis: _containers.RepeatedScalarFieldContainer[str]
    need_rank: bool
    option: _containers.ScalarMap[str, str]
    source: Compilation
    test_suite: _containers.RepeatedCompositeFieldContainer[Test]
    def __init__(self, option: _Optional[_Mapping[str, str]] = ..., source: _Optional[_Union[Compilation, _Mapping]] = ..., analysis: _Optional[_Iterable[str]] = ..., alarm_rel: _Optional[_Iterable[str]] = ..., need_rank: bool = ..., test_suite: _Optional[_Iterable[_Union[Test, _Mapping]]] = ...) -> None: ...

class ApplicationResponse(_message.Message):
    __slots__ = ["alarm", "msg"]
    ALARM_FIELD_NUMBER: _ClassVar[int]
    MSG_FIELD_NUMBER: _ClassVar[int]
    alarm: _containers.RepeatedScalarFieldContainer[str]
    msg: str
    def __init__(self, msg: _Optional[str] = ..., alarm: _Optional[_Iterable[str]] = ...) -> None: ...

class Compilation(_message.Message):
    __slots__ = ["command", "source"]
    COMMAND_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    command: str
    source: str
    def __init__(self, source: _Optional[str] = ..., command: _Optional[str] = ...) -> None: ...

class Test(_message.Message):
    __slots__ = ["arg", "test_id"]
    ARG_FIELD_NUMBER: _ClassVar[int]
    TEST_ID_FIELD_NUMBER: _ClassVar[int]
    arg: _containers.RepeatedScalarFieldContainer[str]
    test_id: str
    def __init__(self, arg: _Optional[_Iterable[str]] = ..., test_id: _Optional[str] = ...) -> None: ...
