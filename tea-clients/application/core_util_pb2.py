# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: application/core_util.proto
"""Generated protocol buffer code."""
from google.protobuf.internal import builder as _builder
from google.protobuf import descriptor as _descriptor
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor_pool.Default().AddSerializedFile(b'\n\x1b\x61pplication/core_util.proto\x12\x08tea.core\".\n\x0b\x43ompilation\x12\x0e\n\x06source\x18\x01 \x01(\t\x12\x0f\n\x07\x63ommand\x18\x02 \x01(\t\"*\n\x04Test\x12\x11\n\tdirectory\x18\x01 \x01(\t\x12\x0f\n\x07test_id\x18\x02 \x03(\t\"\xbb\x02\n\x12\x41pplicationRequest\x12\x12\n\nproject_id\x18\x01 \x01(\t\x12\x38\n\x06option\x18\x02 \x03(\x0b\x32(.tea.core.ApplicationRequest.OptionEntry\x12%\n\x06source\x18\x03 \x01(\x0b\x32\x15.tea.core.Compilation\x12\x10\n\x08\x61nalysis\x18\x04 \x03(\t\x12\x11\n\talarm_rel\x18\x05 \x03(\t\x12\x16\n\tneed_rank\x18\x06 \x01(\x08H\x00\x88\x01\x01\x12\'\n\ntest_suite\x18\x07 \x01(\x0b\x32\x0e.tea.core.TestH\x01\x88\x01\x01\x1a-\n\x0bOptionEntry\x12\x0b\n\x03key\x18\x01 \x01(\t\x12\r\n\x05value\x18\x02 \x01(\t:\x02\x38\x01\x42\x0c\n\n_need_rankB\r\n\x0b_test_suite\">\n\x13\x41pplicationResponse\x12\x10\n\x03msg\x18\x02 \x01(\tH\x00\x88\x01\x01\x12\r\n\x05\x61larm\x18\x03 \x03(\tB\x06\n\x04_msg2]\n\x0b\x43oreService\x12N\n\x0bRunAnalyses\x12\x1c.tea.core.ApplicationRequest\x1a\x1d.tea.core.ApplicationResponse\"\x00\x30\x01\x42\x1c\n\x1a\x63om.neuromancer42.tea.coreb\x06proto3')

_builder.BuildMessageAndEnumDescriptors(DESCRIPTOR, globals())
_builder.BuildTopDescriptorsAndMessages(DESCRIPTOR, 'application.core_util_pb2', globals())
if _descriptor._USE_C_DESCRIPTORS == False:

  DESCRIPTOR._options = None
  DESCRIPTOR._serialized_options = b'\n\032com.neuromancer42.tea.core'
  _APPLICATIONREQUEST_OPTIONENTRY._options = None
  _APPLICATIONREQUEST_OPTIONENTRY._serialized_options = b'8\001'
  _COMPILATION._serialized_start=41
  _COMPILATION._serialized_end=87
  _TEST._serialized_start=89
  _TEST._serialized_end=131
  _APPLICATIONREQUEST._serialized_start=134
  _APPLICATIONREQUEST._serialized_end=449
  _APPLICATIONREQUEST_OPTIONENTRY._serialized_start=375
  _APPLICATIONREQUEST_OPTIONENTRY._serialized_end=420
  _APPLICATIONRESPONSE._serialized_start=451
  _APPLICATIONRESPONSE._serialized_end=513
  _CORESERVICE._serialized_start=515
  _CORESERVICE._serialized_end=608
# @@protoc_insertion_point(module_scope)
