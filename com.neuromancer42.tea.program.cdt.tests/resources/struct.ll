; ModuleID = 'struct.c'
source_filename = "struct.c"
target datalayout = "e-m:o-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx12.0.0"

%struct.TPos = type { i32, i32 }

@__const.main.a = private unnamed_addr constant %struct.TPos { i32 1, i32 2 }, align 4

; Function Attrs: noinline nounwind optnone ssp uwtable
define i32 @main() #0 {
entry:
  %retval = alloca i32, align 4
  %a = alloca %struct.TPos, align 4
  %b = alloca i32, align 4
  %c = alloca %struct.TPos*, align 8
  store i32 0, i32* %retval, align 4
  %0 = bitcast %struct.TPos* %a to i8*
  call void @llvm.memcpy.p0i8.p0i8.i64(i8* align 4 %0, i8* align 4 bitcast (%struct.TPos* @__const.main.a to i8*), i64 8, i1 false)
  %x = getelementptr inbounds %struct.TPos, %struct.TPos* %a, i32 0, i32 0
  %1 = load i32, i32* %x, align 4
  store i32 %1, i32* %b, align 4
  store %struct.TPos* %a, %struct.TPos** %c, align 8
  %2 = load i32, i32* %b, align 4
  %3 = load %struct.TPos*, %struct.TPos** %c, align 8
  %y = getelementptr inbounds %struct.TPos, %struct.TPos* %3, i32 0, i32 1
  store i32 %2, i32* %y, align 4
  ret i32 0
}

; Function Attrs: argmemonly nofree nounwind willreturn
declare void @llvm.memcpy.p0i8.p0i8.i64(i8* noalias nocapture writeonly, i8* noalias nocapture readonly, i64, i1 immarg) #1

attributes #0 = { noinline nounwind optnone ssp uwtable "darwin-stkchk-strong-link" "frame-pointer"="all" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="___chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" }
attributes #1 = { argmemonly nofree nounwind willreturn }

!llvm.module.flags = !{!0, !1, !2, !3, !4}
!llvm.ident = !{!5}

!0 = !{i32 2, !"SDK Version", [2 x i32] [i32 12, i32 3]}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{i32 7, !"PIC Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 1}
!4 = !{i32 7, !"frame-pointer", i32 2}
!5 = !{!"Apple clang version 13.1.6 (clang-1316.0.21.2.5)"}
