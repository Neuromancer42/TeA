; ModuleID = 'array.c'
source_filename = "array.c"
target datalayout = "e-m:o-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx12.0.0"

@a = common global [2 x i32] zeroinitializer, align 4

; Function Attrs: noinline nounwind optnone ssp uwtable
define i32 @main() #0 {
entry:
  %retval = alloca i32, align 4
  %y = alloca i32*, align 8
  %x = alloca i32, align 4
  %w = alloca i64, align 8
  %b = alloca [20 x i32], align 16
  %c = alloca [20 x i32], align 16
  %z = alloca [2 x [1 x i32]], align 4
  store i32 0, i32* %retval, align 4
  store i32 0, i32* %x, align 4
  %0 = load i32, i32* %x, align 4
  %add = add nsw i32 %0, 11
  %conv = sext i32 %add to i64
  store i64 %conv, i64* %w, align 8
  %1 = bitcast [20 x i32]* %b to i8*
  call void @llvm.memset.p0i8.i64(i8* align 16 %1, i8 0, i64 80, i1 false)
  %arrayinit.begin = getelementptr inbounds [20 x i32], [20 x i32]* %b, i64 0, i64 0
  %2 = load i32, i32* %x, align 4
  %add1 = add nsw i32 %2, 1
  store i32 %add1, i32* %arrayinit.begin, align 4
  %arrayinit.element = getelementptr inbounds i32, i32* %arrayinit.begin, i64 1
  %3 = load i32, i32* %x, align 4
  %add2 = add nsw i32 %3, 2
  store i32 %add2, i32* %arrayinit.element, align 4
  %4 = bitcast [20 x i32]* %c to i8*
  call void @llvm.memset.p0i8.i64(i8* align 16 %4, i8 0, i64 80, i1 false)
  %arrayinit.begin3 = getelementptr inbounds [20 x i32], [20 x i32]* %c, i64 0, i64 0
  %arrayinit.element4 = getelementptr inbounds i32, i32* %arrayinit.begin3, i64 1
  %5 = load i32, i32* %x, align 4
  %add5 = add nsw i32 %5, 3
  store i32 %add5, i32* %arrayinit.element4, align 4
  %arrayinit.begin6 = getelementptr inbounds [2 x [1 x i32]], [2 x [1 x i32]]* %z, i64 0, i64 0
  %6 = bitcast [1 x i32]* %arrayinit.begin6 to i8*
  call void @llvm.memset.p0i8.i64(i8* align 4 %6, i8 0, i64 4, i1 false)
  %arrayinit.element7 = getelementptr inbounds [1 x i32], [1 x i32]* %arrayinit.begin6, i64 1
  %arrayinit.begin8 = getelementptr inbounds [1 x i32], [1 x i32]* %arrayinit.element7, i64 0, i64 0
  %7 = load i32, i32* %x, align 4
  store i32 %7, i32* %arrayinit.begin8, align 4
  %arrayidx = getelementptr inbounds [2 x [1 x i32]], [2 x [1 x i32]]* %z, i64 0, i64 0
  %arrayidx9 = getelementptr inbounds [1 x i32], [1 x i32]* %arrayidx, i64 0, i64 0
  %8 = load i32, i32* %arrayidx9, align 4
  %9 = load i32, i32* %x, align 4
  %idxprom = sext i32 %9 to i64
  %arrayidx10 = getelementptr inbounds [2 x i32], [2 x i32]* @a, i64 0, i64 %idxprom
  store i32 %8, i32* %arrayidx10, align 4
  store i32* getelementptr inbounds ([2 x i32], [2 x i32]* @a, i64 0, i64 1), i32** %y, align 8
  %10 = load i32*, i32** %y, align 8
  %add.ptr = getelementptr inbounds i32, i32* %10, i64 1
  %11 = load i32, i32* %add.ptr, align 4
  store i32 %11, i32* %x, align 4
  %12 = load i32, i32* %x, align 4
  ret i32 %12
}

; Function Attrs: argmemonly nofree nounwind willreturn writeonly
declare void @llvm.memset.p0i8.i64(i8* nocapture writeonly, i8, i64, i1 immarg) #1

attributes #0 = { noinline nounwind optnone ssp uwtable "darwin-stkchk-strong-link" "frame-pointer"="all" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="___chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" }
attributes #1 = { argmemonly nofree nounwind willreturn writeonly }

!llvm.module.flags = !{!0, !1, !2, !3, !4}
!llvm.ident = !{!5}

!0 = !{i32 2, !"SDK Version", [2 x i32] [i32 12, i32 3]}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{i32 7, !"PIC Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 1}
!4 = !{i32 7, !"frame-pointer", i32 2}
!5 = !{!"Apple clang version 13.1.6 (clang-1316.0.21.2.5)"}
