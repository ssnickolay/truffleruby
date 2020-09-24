# frozen_string_literal: true

# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Generated by tool/generate-pointer-methods.rb

class Truffle::FFI::Pointer
  # char, int8

  def read_char
    check_bounds(0, 1)
    Primitive.pointer_read_char address
  end
  alias_method :read_int8, :read_char

  def write_char(value)
    check_bounds(0, 1)
    Primitive.pointer_write_char address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_int8, :write_char

  def get_char(offset)
    check_bounds(offset, 1)
    Primitive.pointer_read_char address + offset
  end
  alias_method :get_int8, :get_char

  def put_char(offset, value)
    check_bounds(offset, 1)
    Primitive.pointer_write_char address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_int8, :put_char

  def read_array_of_char(length)
    check_bounds(0, length * 1)
    Array.new(length) do |i|
      Primitive.pointer_read_char address + (i * 1)
    end
  end
  alias_method :read_array_of_int8, :read_array_of_char

  def write_array_of_char(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 1)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_char address + (i * 1), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_int8, :write_array_of_char

  def get_array_of_char(offset, length)
    (self + offset).read_array_of_char(length)
  end
  alias_method :get_array_of_int8, :get_array_of_char

  def put_array_of_char(ary)
    (self + offset).write_array_of_char(ary)
    self
  end
  alias_method :put_array_of_int8, :put_array_of_char

  # uchar, uint8

  def read_uchar
    check_bounds(0, 1)
    Primitive.pointer_read_uchar address
  end
  alias_method :read_uint8, :read_uchar

  def write_uchar(value)
    check_bounds(0, 1)
    Primitive.pointer_write_uchar address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_uint8, :write_uchar

  def get_uchar(offset)
    check_bounds(offset, 1)
    Primitive.pointer_read_uchar address + offset
  end
  alias_method :get_uint8, :get_uchar

  def put_uchar(offset, value)
    check_bounds(offset, 1)
    Primitive.pointer_write_uchar address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_uint8, :put_uchar

  def read_array_of_uchar(length)
    check_bounds(0, length * 1)
    Array.new(length) do |i|
      Primitive.pointer_read_uchar address + (i * 1)
    end
  end
  alias_method :read_array_of_uint8, :read_array_of_uchar

  def write_array_of_uchar(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 1)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_uchar address + (i * 1), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_uint8, :write_array_of_uchar

  def get_array_of_uchar(offset, length)
    (self + offset).read_array_of_uchar(length)
  end
  alias_method :get_array_of_uint8, :get_array_of_uchar

  def put_array_of_uchar(ary)
    (self + offset).write_array_of_uchar(ary)
    self
  end
  alias_method :put_array_of_uint8, :put_array_of_uchar

  # short, int16

  def read_short
    check_bounds(0, 2)
    Primitive.pointer_read_short address
  end
  alias_method :read_int16, :read_short

  def write_short(value)
    check_bounds(0, 2)
    Primitive.pointer_write_short address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_int16, :write_short

  def get_short(offset)
    check_bounds(offset, 2)
    Primitive.pointer_read_short address + offset
  end
  alias_method :get_int16, :get_short

  def put_short(offset, value)
    check_bounds(offset, 2)
    Primitive.pointer_write_short address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_int16, :put_short

  def read_array_of_short(length)
    check_bounds(0, length * 2)
    Array.new(length) do |i|
      Primitive.pointer_read_short address + (i * 2)
    end
  end
  alias_method :read_array_of_int16, :read_array_of_short

  def write_array_of_short(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 2)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_short address + (i * 2), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_int16, :write_array_of_short

  def get_array_of_short(offset, length)
    (self + offset).read_array_of_short(length)
  end
  alias_method :get_array_of_int16, :get_array_of_short

  def put_array_of_short(ary)
    (self + offset).write_array_of_short(ary)
    self
  end
  alias_method :put_array_of_int16, :put_array_of_short

  # ushort, uint16

  def read_ushort
    check_bounds(0, 2)
    Primitive.pointer_read_ushort address
  end
  alias_method :read_uint16, :read_ushort

  def write_ushort(value)
    check_bounds(0, 2)
    Primitive.pointer_write_ushort address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_uint16, :write_ushort

  def get_ushort(offset)
    check_bounds(offset, 2)
    Primitive.pointer_read_ushort address + offset
  end
  alias_method :get_uint16, :get_ushort

  def put_ushort(offset, value)
    check_bounds(offset, 2)
    Primitive.pointer_write_ushort address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_uint16, :put_ushort

  def read_array_of_ushort(length)
    check_bounds(0, length * 2)
    Array.new(length) do |i|
      Primitive.pointer_read_ushort address + (i * 2)
    end
  end
  alias_method :read_array_of_uint16, :read_array_of_ushort

  def write_array_of_ushort(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 2)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_ushort address + (i * 2), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_uint16, :write_array_of_ushort

  def get_array_of_ushort(offset, length)
    (self + offset).read_array_of_ushort(length)
  end
  alias_method :get_array_of_uint16, :get_array_of_ushort

  def put_array_of_ushort(ary)
    (self + offset).write_array_of_ushort(ary)
    self
  end
  alias_method :put_array_of_uint16, :put_array_of_ushort

  # int, int32

  def read_int
    check_bounds(0, 4)
    Primitive.pointer_read_int address
  end
  alias_method :read_int32, :read_int

  def write_int(value)
    check_bounds(0, 4)
    Primitive.pointer_write_int address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_int32, :write_int

  def get_int(offset)
    check_bounds(offset, 4)
    Primitive.pointer_read_int address + offset
  end
  alias_method :get_int32, :get_int

  def put_int(offset, value)
    check_bounds(offset, 4)
    Primitive.pointer_write_int address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_int32, :put_int

  def read_array_of_int(length)
    check_bounds(0, length * 4)
    Array.new(length) do |i|
      Primitive.pointer_read_int address + (i * 4)
    end
  end
  alias_method :read_array_of_int32, :read_array_of_int

  def write_array_of_int(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 4)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_int address + (i * 4), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_int32, :write_array_of_int

  def get_array_of_int(offset, length)
    (self + offset).read_array_of_int(length)
  end
  alias_method :get_array_of_int32, :get_array_of_int

  def put_array_of_int(ary)
    (self + offset).write_array_of_int(ary)
    self
  end
  alias_method :put_array_of_int32, :put_array_of_int

  # uint, uint32

  def read_uint
    check_bounds(0, 4)
    Primitive.pointer_read_uint address
  end
  alias_method :read_uint32, :read_uint

  def write_uint(value)
    check_bounds(0, 4)
    Primitive.pointer_write_uint address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_uint32, :write_uint

  def get_uint(offset)
    check_bounds(offset, 4)
    Primitive.pointer_read_uint address + offset
  end
  alias_method :get_uint32, :get_uint

  def put_uint(offset, value)
    check_bounds(offset, 4)
    Primitive.pointer_write_uint address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_uint32, :put_uint

  def read_array_of_uint(length)
    check_bounds(0, length * 4)
    Array.new(length) do |i|
      Primitive.pointer_read_uint address + (i * 4)
    end
  end
  alias_method :read_array_of_uint32, :read_array_of_uint

  def write_array_of_uint(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 4)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_uint address + (i * 4), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_uint32, :write_array_of_uint

  def get_array_of_uint(offset, length)
    (self + offset).read_array_of_uint(length)
  end
  alias_method :get_array_of_uint32, :get_array_of_uint

  def put_array_of_uint(ary)
    (self + offset).write_array_of_uint(ary)
    self
  end
  alias_method :put_array_of_uint32, :put_array_of_uint

  # long, int64, long_long

  def read_long
    check_bounds(0, 8)
    Primitive.pointer_read_long address
  end
  alias_method :read_int64, :read_long
  alias_method :read_long_long, :read_long

  def write_long(value)
    check_bounds(0, 8)
    Primitive.pointer_write_long address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_int64, :write_long
  alias_method :write_long_long, :write_long

  def get_long(offset)
    check_bounds(offset, 8)
    Primitive.pointer_read_long address + offset
  end
  alias_method :get_int64, :get_long
  alias_method :get_long_long, :get_long

  def put_long(offset, value)
    check_bounds(offset, 8)
    Primitive.pointer_write_long address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_int64, :put_long
  alias_method :put_long_long, :put_long

  def read_array_of_long(length)
    check_bounds(0, length * 8)
    Array.new(length) do |i|
      Primitive.pointer_read_long address + (i * 8)
    end
  end
  alias_method :read_array_of_int64, :read_array_of_long
  alias_method :read_array_of_long_long, :read_array_of_long

  def write_array_of_long(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 8)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_long address + (i * 8), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_int64, :write_array_of_long
  alias_method :write_array_of_long_long, :write_array_of_long

  def get_array_of_long(offset, length)
    (self + offset).read_array_of_long(length)
  end
  alias_method :get_array_of_int64, :get_array_of_long
  alias_method :get_array_of_long_long, :get_array_of_long

  def put_array_of_long(ary)
    (self + offset).write_array_of_long(ary)
    self
  end
  alias_method :put_array_of_int64, :put_array_of_long
  alias_method :put_array_of_long_long, :put_array_of_long

  # ulong, uint64, ulong_long

  def read_ulong
    check_bounds(0, 8)
    Primitive.pointer_read_ulong address
  end
  alias_method :read_uint64, :read_ulong
  alias_method :read_ulong_long, :read_ulong

  def write_ulong(value)
    check_bounds(0, 8)
    Primitive.pointer_write_ulong address, Primitive.rb_to_int(value)
    self
  end
  alias_method :write_uint64, :write_ulong
  alias_method :write_ulong_long, :write_ulong

  def get_ulong(offset)
    check_bounds(offset, 8)
    Primitive.pointer_read_ulong address + offset
  end
  alias_method :get_uint64, :get_ulong
  alias_method :get_ulong_long, :get_ulong

  def put_ulong(offset, value)
    check_bounds(offset, 8)
    Primitive.pointer_write_ulong address + offset, Primitive.rb_to_int(value)
    self
  end
  alias_method :put_uint64, :put_ulong
  alias_method :put_ulong_long, :put_ulong

  def read_array_of_ulong(length)
    check_bounds(0, length * 8)
    Array.new(length) do |i|
      Primitive.pointer_read_ulong address + (i * 8)
    end
  end
  alias_method :read_array_of_uint64, :read_array_of_ulong
  alias_method :read_array_of_ulong_long, :read_array_of_ulong

  def write_array_of_ulong(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 8)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_ulong address + (i * 8), Primitive.rb_to_int(value)
    end
    self
  end
  alias_method :write_array_of_uint64, :write_array_of_ulong
  alias_method :write_array_of_ulong_long, :write_array_of_ulong

  def get_array_of_ulong(offset, length)
    (self + offset).read_array_of_ulong(length)
  end
  alias_method :get_array_of_uint64, :get_array_of_ulong
  alias_method :get_array_of_ulong_long, :get_array_of_ulong

  def put_array_of_ulong(ary)
    (self + offset).write_array_of_ulong(ary)
    self
  end
  alias_method :put_array_of_uint64, :put_array_of_ulong
  alias_method :put_array_of_ulong_long, :put_array_of_ulong

  # float, float32

  def read_float
    check_bounds(0, 4)
    Primitive.pointer_read_float address
  end
  alias_method :read_float32, :read_float

  def write_float(value)
    check_bounds(0, 4)
    Primitive.pointer_write_float address, Truffle::Type.rb_to_f(value)
    self
  end
  alias_method :write_float32, :write_float

  def get_float(offset)
    check_bounds(offset, 4)
    Primitive.pointer_read_float address + offset
  end
  alias_method :get_float32, :get_float

  def put_float(offset, value)
    check_bounds(offset, 4)
    Primitive.pointer_write_float address + offset, Truffle::Type.rb_to_f(value)
    self
  end
  alias_method :put_float32, :put_float

  def read_array_of_float(length)
    check_bounds(0, length * 4)
    Array.new(length) do |i|
      Primitive.pointer_read_float address + (i * 4)
    end
  end
  alias_method :read_array_of_float32, :read_array_of_float

  def write_array_of_float(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 4)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_float address + (i * 4), Truffle::Type.rb_to_f(value)
    end
    self
  end
  alias_method :write_array_of_float32, :write_array_of_float

  def get_array_of_float(offset, length)
    (self + offset).read_array_of_float(length)
  end
  alias_method :get_array_of_float32, :get_array_of_float

  def put_array_of_float(ary)
    (self + offset).write_array_of_float(ary)
    self
  end
  alias_method :put_array_of_float32, :put_array_of_float

  # double, float64

  def read_double
    check_bounds(0, 8)
    Primitive.pointer_read_double address
  end
  alias_method :read_float64, :read_double

  def write_double(value)
    check_bounds(0, 8)
    Primitive.pointer_write_double address, Truffle::Type.rb_to_f(value)
    self
  end
  alias_method :write_float64, :write_double

  def get_double(offset)
    check_bounds(offset, 8)
    Primitive.pointer_read_double address + offset
  end
  alias_method :get_float64, :get_double

  def put_double(offset, value)
    check_bounds(offset, 8)
    Primitive.pointer_write_double address + offset, Truffle::Type.rb_to_f(value)
    self
  end
  alias_method :put_float64, :put_double

  def read_array_of_double(length)
    check_bounds(0, length * 8)
    Array.new(length) do |i|
      Primitive.pointer_read_double address + (i * 8)
    end
  end
  alias_method :read_array_of_float64, :read_array_of_double

  def write_array_of_double(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 8)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_double address + (i * 8), Truffle::Type.rb_to_f(value)
    end
    self
  end
  alias_method :write_array_of_float64, :write_array_of_double

  def get_array_of_double(offset, length)
    (self + offset).read_array_of_double(length)
  end
  alias_method :get_array_of_float64, :get_array_of_double

  def put_array_of_double(ary)
    (self + offset).write_array_of_double(ary)
    self
  end
  alias_method :put_array_of_float64, :put_array_of_double

  # pointer

  private def get_pointer_value(value)
    if Truffle::FFI::Pointer === value
      value.address
    elsif nil.equal?(value)
      0
    elsif Integer === value
      value
    elsif value.respond_to?(:to_ptr)
      value.to_ptr.address
    else
      raise ArgumentError, "#{value} is not a pointer"
    end
  end

  def read_pointer
    check_bounds(0, 8)
    Primitive.pointer_read_pointer address
  end

  def write_pointer(value)
    check_bounds(0, 8)
    Primitive.pointer_write_pointer address, get_pointer_value(value)
    self
  end

  def get_pointer(offset)
    check_bounds(offset, 8)
    Primitive.pointer_read_pointer address + offset
  end

  def put_pointer(offset, value)
    check_bounds(offset, 8)
    Primitive.pointer_write_pointer address + offset, get_pointer_value(value)
    self
  end

  def read_array_of_pointer(length)
    check_bounds(0, length * 8)
    Array.new(length) do |i|
      Primitive.pointer_read_pointer address + (i * 8)
    end
  end

  def write_array_of_pointer(ary)
    Truffle::Type.rb_check_type(ary, ::Array)
    check_bounds(0, ary.size * 8)
    ary.each_with_index do |value, i|
      Primitive.pointer_write_pointer address + (i * 8), get_pointer_value(value)
    end
    self
  end

  def get_array_of_pointer(offset, length)
    (self + offset).read_array_of_pointer(length)
  end

  def put_array_of_pointer(ary)
    (self + offset).write_array_of_pointer(ary)
    self
  end

end
