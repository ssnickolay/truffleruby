require_relative '../../spec_helper'
require_relative 'fixtures/refine'

describe "Module#refine" do
    ruby_version_is "2.6" do
      it "is honored by Kernel#respond_to?" do
        klass = Class.new
        refinement = Module.new do
          refine klass do
            def foo; end
          end
        end

        result = nil
        Module.new do
          using refinement
          result = klass.new.respond_to?(:foo)
        end

        result.should == true
      end
    end
end